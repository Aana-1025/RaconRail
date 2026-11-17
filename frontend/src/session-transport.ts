export type SessionInfo = { accountId: string; workspaceId: string; sessionGeneration: string; expiresAt: string; capabilities: string[] };
export class SessionTransport {
  private epoch = 0;
  private csrf?: string;
  private identity?: string;
  private listeners = new Set<(info?:SessionInfo)=>void>();
  subscribe(listener:(info?:SessionInfo)=>void) { this.listeners.add(listener);return ()=>{this.listeners.delete(listener);}; }
  private controllers = new Set<AbortController>();
  constructor(private clearPrivate: () => void, private request: typeof fetch = (input,init)=>fetch(input,init)) {}
  invalidate() { this.epoch++; this.csrf=undefined;this.identity=undefined;for(const c of this.controllers)c.abort();this.controllers.clear();this.clearPrivate();for(const listener of this.listeners)listener(undefined); }
  async session(): Promise<SessionInfo | undefined> {
    const e=this.epoch;const c=new AbortController();this.controllers.add(c);
    try {
      const response=await this.request('/api/session',{credentials:'same-origin',cache:'no-store',signal:c.signal});
      if(e!==this.epoch)throw new Error('STALE_SESSION_RESPONSE');
      if(response.status===401||response.status===404){this.invalidate();return undefined;}
      if(!response.ok)throw new Error('SESSION_UNAVAILABLE');
      const info=await response.json() as SessionInfo;if(e!==this.epoch)throw new Error('STALE_SESSION_RESPONSE');
      const identity=`${info.accountId}:${info.sessionGeneration}`;
      if(this.identity && identity!==this.identity){this.invalidate();throw new Error('SESSION_CHANGED');}
      this.identity=identity;for(const listener of this.listeners)listener(info);return info;
    } finally { this.controllers.delete(c); }
  }
  async logout(): Promise<void> {
    try {
      const e=this.epoch;
      const response=await this.request('/api/csrf',{credentials:'same-origin',cache:'no-store'});
      if(!response.ok||e!==this.epoch)throw new Error('SESSION_UNAVAILABLE');
      const csrf=await response.json() as {token:string;headerName:string};if(e!==this.epoch||csrf.headerName!=='X-CSRF-TOKEN')throw new Error('SESSION_CHANGED');this.csrf=csrf.token;
      const result=await this.request('/api/auth/logout',{method:'POST',credentials:'same-origin',cache:'no-store',headers:{'X-CSRF-TOKEN':this.csrf}});
      if(!result.ok&&result.status!==401)throw new Error('LOCAL_LOGOUT_FAILED');
    } finally { this.invalidate(); }
  }
  watch(): () => void {
    const revalidate=()=>{this.invalidate();void this.session().catch(()=>this.invalidate());};
    const visible=()=>{if(document.visibilityState==='visible')revalidate();};
    window.addEventListener('pageshow',revalidate);document.addEventListener('visibilitychange',visible);
    return ()=>{window.removeEventListener('pageshow',revalidate);document.removeEventListener('visibilitychange',visible);this.invalidate();};
  }
}
