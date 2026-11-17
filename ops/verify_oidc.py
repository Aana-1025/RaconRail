"""Real local Keycloak authorization-code browser protocol checks; synthetic identities only."""
import html,json,os,re,secrets,socket,subprocess,time,tempfile
import http.cookiejar
from pathlib import Path
from urllib.parse import urlsplit,urlunsplit,parse_qsl,urlencode
import requests
IMAGE='quay.io/keycloak/keycloak@sha256:c68e286cacd87b942ac1939a0a1481461109d6acd3a57dc496969a319a76bbf0'
def verify(root,env,evidence):
    stage='setup';container=None;app=None;checks=[]
    def port():
        with socket.socket() as s:s.bind(('127.0.0.1',0));return s.getsockname()[1]
    def run(args):
        p=subprocess.run(args,capture_output=True,text=True,timeout=120)
        if p.returncode:raise RuntimeError('Owned integration resource operation failed')
        return p.stdout.strip()
    kcport=port();appport=port();origin=f'http://127.0.0.1:{appport}';issuer=f'http://127.0.0.1:{kcport}/realms/reconrail'
    class LoopbackCookiePolicy(http.cookiejar.DefaultCookiePolicy):
        def return_ok_secure(self,cookie,request):
            uri=urlsplit(request.get_full_url())
            if uri.scheme=='http' and uri.hostname=='127.0.0.1' and uri.port==kcport:return True
            return super().return_ok_secure(cookie,request)
    class BrowserSession(requests.Session):
        # Match the real Chromium loopback-cookie behavior observed in browser tests.
        # Server flags stay intact; this applies only to this owned local IdP port.
        def prepare_request(self,request):
            prepared=super().prepare_request(request)
            prepared._cookies.set_policy(LoopbackCookiePolicy())
            prepared.headers.pop('Cookie',None)
            prepared.prepare_cookies(prepared._cookies)
            return prepared
    try:
        if not (root/'tmp').resolve().is_relative_to(root.resolve()):raise RuntimeError('Unsafe temporary root')
        with tempfile.TemporaryDirectory(prefix='rr-oidc-',dir=root/'tmp') as directory:
            temp=Path(directory);client_secret=secrets.token_urlsafe(32);passwords={u:secrets.token_urlsafe(20) for u in ['alice','bob']}
            realm={'realm':'reconrail','enabled':True,'duplicateEmailsAllowed':True,'registrationAllowed':False,'resetPasswordAllowed':False,'clients':[{'clientId':'reconrail-local','enabled':True,'publicClient':False,'secret':client_secret,'standardFlowEnabled':True,'directAccessGrantsEnabled':False,'redirectUris':[origin+'/login/oauth2/code/reconrail'],'webOrigins':[origin],'attributes':{'pkce.code.challenge.method':'S256'}}],'users':[{'username':u,'firstName':'Local','lastName':'Fixture','enabled':True,'email':'same-synthetic@example.invalid','emailVerified':True,'credentials':[{'type':'password','value':passwords[u],'temporary':False}]} for u in passwords]}
            realmfile=temp/'realm.json';realmfile.write_text(json.dumps(realm),encoding='utf-8')
            container=run(['docker','run','--detach','--rm','--name','rr-idp-'+secrets.token_hex(5),'--label','dev.reconrail.test=oidc','--memory','1g','--cpus','2','--publish',f'127.0.0.1:{kcport}:8080','--mount',f'type=bind,source={realmfile},target=/opt/keycloak/data/import/realm.json,readonly',IMAGE,'start-dev','--import-realm',f'--hostname=http://127.0.0.1:{kcport}','--http-enabled=true'])
            for n in range(90):
                try:
                    r=requests.get(issuer+'/.well-known/openid-configuration',timeout=2)
                    if r.status_code==200:break
                except requests.RequestException:pass
                time.sleep(1)
            else:raise RuntimeError('IdP readiness failed')
            checks.append({'name':'real local Keycloak discovery ready','status':'PASS'})
            appenv=env.copy();appenv.update(SPRING_PROFILES_ACTIVE='auth',RR_AUTH_DEVELOPMENT='true',RR_AUTH_ORIGIN=origin,RR_AUTH_ISSUER=issuer,RR_AUTH_CLIENT_ID='reconrail-local',RR_AUTH_CLIENT_SECRET=client_secret,RR_AUTH_JDBC_URL=env['RR_TEST_JDBC_URL'],RR_AUTH_JDBC_USER=env['RR_TEST_JDBC_USER'],RR_AUTH_JDBC_PASSWORD=env['RR_TEST_JDBC_PASSWORD'])
            # Boot relaxed names map these environment variables to rr.auth.* properties.
            logfile=(evidence/'oidc-app.log').open('w',encoding='utf-8')
            java=str(Path(env['JAVA_HOME'])/'bin'/('java.exe' if os.name=='nt' else 'java'))
            app=subprocess.Popen([java,'-Duser.timezone=UTC','-jar',str(root/'target/reconrail-0.1.0-SNAPSHOT.jar'),f'--server.port={appport}'],env=appenv,stdout=logfile,stderr=subprocess.STDOUT)
            for n in range(60):
                try:
                    r=requests.get(origin+'/api/session',timeout=2)
                    if r.status_code==401:break
                except requests.RequestException:pass
                if app.poll() is not None:raise RuntimeError('Auth application startup failed')
                time.sleep(1)
            else:raise RuntimeError('Auth application readiness failed')
            if os.environ.get('RR_BROWSER_HOLD')=='1':
                control=root/'.local-tools/research/rr06/browser-control.json';release=root/'.local-tools/research/rr06/browser-release'
                if release.exists():release.unlink()
                control.write_text(json.dumps({'origin':origin,'realm_file':str(realmfile),'release':str(release)}))
                print('READY FOR LOCAL BROWSER CHECK '+origin,flush=True)
                for attempt in range(1800):
                    if release.exists():break
                    time.sleep(1)
                else:raise RuntimeError('Browser verification handoff timed out')
            def check(name,condition):
                if not condition:raise RuntimeError('Assertion failed: '+name)
                checks.append({'name':name,'status':'PASS'});print('PASS '+name,flush=True)
            def redirects(response,path):
                return response.headers.get('Location') in (path,origin+path)
            def login(browser,user,mutation=None):
                r=browser.get(origin+'/oauth2/authorization/reconrail',allow_redirects=False,timeout=5);assert r.status_code in [302,303]
                location=r.headers['Location'];parts=urlsplit(location);query=dict(parse_qsl(parts.query));check('S256 PKCE and state/nonce requested',query.get('code_challenge_method')=='S256' and bool(query.get('state')) and bool(query.get('nonce')) and query.get('scope')=='openid')
                if mutation=='nonce':query['nonce']='wrong-nonce'
                if mutation=='pkce':query['code_challenge']='A'*43
                if mutation:location=urlunsplit((parts.scheme,parts.netloc,parts.path,urlencode(query),parts.fragment))
                if mutation=='state':
                    bad=browser.get(origin+'/login/oauth2/code/reconrail',params={'code':'invalid','state':'wrong-state'},allow_redirects=False,timeout=5);check('wrong state has bounded failure',bad.status_code in [302,401,403])
                form=browser.get(location,timeout=10);match=re.search(r'<form\b(?=[^>]*\bid="kc-form-login")[^>]*\baction="([^"]+)"',form.text)
                if not match:raise RuntimeError('Missing callback; sign-in form IDs '+str(re.findall(r'<form[^>]*id="([^"]+)"',form.text)))
                response=browser.post(html.unescape(match.group(1)),data={'username':user,'password':passwords[user],'credentialId':''},allow_redirects=False,timeout=10)
                callback=response.headers.get('Location','')
                if not callback.startswith(origin+'/login/oauth2/code/reconrail?'):raise RuntimeError('Missing callback; submit status '+str(response.status_code)+'; profile action '+str('required-action' in response.text)+'; invalid credential '+str('Invalid username or password' in response.text)+'; cookie error '+str('Cookie not found' in response.text)+'; invalid request '+str('Invalid' in response.text))
                response=browser.get(callback,allow_redirects=False,timeout=15)
                if not redirects(response,'/cases'):
                    (evidence/'callback-result.json').write_text(json.dumps({'status':response.status_code,'redirect_path':urlsplit(response.headers.get('Location','')).path,'body_title':re.search(r'<title>([^<]{0,80})</title>',response.text).group(1) if re.search(r'<title>([^<]{0,80})</title>',response.text) else None}))
                return response,callback
            availability=requests.get(origin+'/api/auth/availability',timeout=5)
            check('auth availability is public and explicitly enabled',availability.status_code==200 and availability.json()=={'enabled':True})
            check('unexpected Host is rejected',requests.get(origin+'/api/auth/availability',headers={'Host':'foreign.invalid'},timeout=5).status_code==400)
            browser=BrowserSession();stage='success-login';response,callback=login(browser,'alice','state');check('valid code signs in through real IdP',response.status_code in [302,303] and redirects(response,'/cases'))
            who=browser.get(origin+'/api/session',timeout=5);check('token-free internal session JSON',who.status_code==200 and set(who.json())=={'accountId','workspaceId','sessionGeneration','expiresAt','capabilities'});alice=who.json()
            check('private responses forbid caching and referrers','no-store' in who.headers.get('Cache-Control','') and who.headers.get('Referrer-Policy')=='no-referrer')
            check('session cookie flags',any(c.name=='rr_dev_session' for c in browser.cookies))
            stage='replay';replay=browser.get(callback,allow_redirects=False,timeout=10);check('used authorization code/state cannot sign in again',redirects(replay,'/sign-in?error=login'))
            stage='second-subject';other=BrowserSession();r,_=login(other,'bob');bob=other.get(origin+'/api/session',timeout=5);check('two subjects with same email stay isolated',bob.status_code==200 and bob.json()['accountId']!=alice['accountId'])
            stage='wrong-subject-reauthentication'
            cookie_before=next(c.value for c in browser.cookies if c.name=='rr_dev_session')
            for cookie in list(browser.cookies):
                if cookie.path.startswith('/realms/'):
                    browser.cookies.clear(cookie.domain,cookie.path,cookie.name)
            r,_=login(browser,'bob')
            check('different verified subject cannot reauthenticate an existing account',redirects(r,'/sign-in?error=login'))
            still=browser.get(origin+'/api/session',timeout=5)
            check('wrong subject leaves original authority and session ID unchanged',still.status_code==200 and still.json()['accountId']==alice['accountId'] and cookie_before==next(c.value for c in browser.cookies if c.name=='rr_dev_session'))
            for mutation in ['nonce','pkce']:
                stage='wrong-'+mutation;attack=BrowserSession();r,_=login(attack,'alice',mutation);check('wrong '+mutation+' proof denied',redirects(r,'/sign-in?error=login') and attack.get(origin+'/api/session',timeout=5).status_code==401)
            stage='csrf';csrf=browser.get(origin+'/api/csrf',timeout=5).json();check('CSRF token bootstrap',csrf.get('headerName')=='X-CSRF-TOKEN')
            check('missing CSRF denies logout',browser.post(origin+'/api/auth/logout',headers={'Origin':origin},timeout=5).status_code==403)
            check('cross-origin logout denied',browser.post(origin+'/api/auth/logout',headers={'Origin':'http://foreign.invalid','X-CSRF-TOKEN':csrf['token']},timeout=5).status_code==403)
            check('failed requests preserve session',browser.get(origin+'/api/session',timeout=5).status_code==200)
            stage='idp-outage';run(['docker','stop',container]);container=None
            check('local session survives IdP outage',browser.get(origin+'/api/session',timeout=5).status_code==200)
            logout=browser.post(origin+'/api/auth/logout',headers={'Origin':origin,'X-CSRF-TOKEN':csrf['token']},timeout=5);check('local logout succeeds during IdP outage',logout.status_code==204)
            check('logout removes local authority',browser.get(origin+'/api/session',timeout=5).status_code==401)
    except Exception as error:
        diagnostic=str(error) if isinstance(error,RuntimeError) and str(error).startswith(('Missing callback','Assertion failed','Auth application','IdP readiness')) else type(error).__name__
        (evidence/'oidc-failure.json').write_text(json.dumps({'stage':stage,'reason':diagnostic}))
        raise RuntimeError('OIDC integration failed at stage: '+stage) from None
    finally:
        if app:
            app.terminate()
            try:app.wait(timeout=15)
            except subprocess.TimeoutExpired:app.kill();app.wait(timeout=10)
            logfile.close()
        if container:run(['docker','rm','--force',container])
        (evidence/'oidc-checks.json').write_text(json.dumps({'checks':checks,'stage':stage,'image':IMAGE,'owned_app_exited':app is None or app.poll() is not None},indent=2)+'\n')
