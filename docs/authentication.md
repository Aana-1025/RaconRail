# Authentication and sessions

The default application is a public preview. Enable the `auth` Spring profile only with a migrated PostgreSQL database and a configured identity provider. No app password login or private investigation workflow is provided.

## Configuration

Supply secrets through the process environment or a private configuration file outside source control. Never put secrets in arguments, browser URLs or logs.

| Variable | Purpose |
| --- | --- |
| `SPRING_PROFILES_ACTIVE=auth` | Enable authentication |
| `RR_AUTH_ORIGIN` | Exact app origin |
| `RR_AUTH_ISSUER` | Exact trusted Keycloak realm issuer |
| `RR_AUTH_CLIENT_ID` | Confidential client identifier |
| `RR_AUTH_CLIENT_SECRET` | Private client secret |
| `RR_AUTH_JDBC_URL` | PostgreSQL JDBC URL |
| `RR_AUTH_JDBC_USER` | Login role inheriting only `rr_control` |
| `RR_AUTH_JDBC_PASSWORD` | Runtime database password |
| `RR_AUTH_DEVELOPMENT=true` | Explicit HTTP loopback-only test mode |

The endpoint adapter targets Keycloak: `/protocol/openid-connect/auth`, `/token` and `/certs` below the issuer. It is not generic discovery. Register exactly `{origin}/login/oauth2/code/reconrail`, enable code flow and require S256 PKCE. Only `openid` is requested. Hosted mode requires an HTTPS origin and refuses automatic forwarded-header trust; actual TLS/ingress/provider validation remains required.

The runtime uses a six-connection control pool. Never supply migration-owner credentials to the app. Apply reviewed migrations offline using the database instructions. V6 lets `rr_control` resolve the verified account's workspace with transaction-local `rr.account_id`; the added policy is SELECT-only. Child tables retain normal workspace scope.

## Behavior and bounds

`/sign-in` navigates to the provider. Signature, issuer, audience, authorized party, expiry, nonce, state and PKCE are checked; RS256 is explicit. Exact issuer/subject binds the internal account. Email, provider roles and usernames never select ownership. Provider tokens are discarded before persistence. Reauthentication cannot switch subjects or extend the original eight-hour bound.

Spring Session JDBC uses explicit allowlisted codecs and AES-256-GCM envelopes bound to session ID, attribute and version. Unknown types/keys, malformed bytes and tag failures invalidate sessions. Encoded attributes are limited to64KiB and total attributes to256KiB/session. Keys rotate before one million encryptions. Fresh process keys invalidate stored sessions before readiness, deliberately signing everyone out on restart. Run one app process; shared key recovery and replicas are not implemented.

Admission/count/insert commits in one transaction with no outer business transaction allowed. Limits: five live sessions/account,1024 service-wide,128 anonymous and two pending login transactions/browser. Pending transactions expire after five minutes. Authentication expires after30minutes idle or eight hours from original login. Polling does not extend activity; private action activity updates are not wired yet. Closed/changed generations and logged-out sessions cannot be revived by a late save.

Hosted cookies use `__Host-rr_session`, Secure, HttpOnly, SameSite=Lax, Path=/ and no Domain. Explicit loopback mode uses separate `rr_dev_session`. POST logout requires server-session CSRF and exact same-origin validation. Local logout invalidates the session and expires its cookie without contacting the provider; it does not promise universal sign-out.

Endpoints: public `GET /api/auth/availability`, public `GET /api/csrf`, authenticated `GET /api/session`, CSRF-protected `POST /api/auth/logout`. Private/CSRF responses forbid caching. Session JSON contains internal IDs, generation, expiry and empty capabilities, with no provider tokens. Unknown private APIs are denied. React clears private query state on expiry/logout/identity change, rejects late responses and revalidates on browser return. Destructive requests are never automatically replayed.

## Verification and remaining scope

With Docker and standard build prerequisites, install `ops/test-requirements.txt` in a suitable Python environment and fetch the pinned PostgreSQL/Keycloak images referenced in the scripts. Run:

```powershell
./ops/rr.ps1 verify -Suite security -Task RR-06
```

Linux: `python3 ops/run_commands.py --suite security --task RR-06`. This runs the build, real nonowner JDBC checks and a disposable Keycloak/app protocol fixture with synthetic identities. Only resources created by that invocation are removed. No existing database or personal account is used. The test client models Chromium's Secure-cookie treatment only at the owned HTTP127.0.0.1 provider port; server cookie flags are retained.

This scope does not establish hosted TLS/cookies, rate-limit/ingress protection, complete accessibility, account-closure intent/confirmation/fencing, restore safety, provider recovery or full product security. The global security/all suites remain explicitly blocked pending those integrations.
