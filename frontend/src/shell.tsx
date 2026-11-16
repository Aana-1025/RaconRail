import { useEffect, useRef, useState } from 'react';
import { Link, NavLink, Outlet, useLocation } from 'react-router';
import styles from './shell.module.css';

const Arrow = () => <span aria-hidden="true">↗</span>;
export function Shell() {
  const location = useLocation();
  const main = useRef<HTMLElement>(null);
  useEffect(() => { main.current?.querySelector<HTMLHeadingElement>('h1')?.focus(); }, [location.pathname]);
  return <div className={styles.app}>
    <a className={styles.skip} href="#main">Skip to content</a>
    <header className={styles.header}>
      <Link className={styles.brand} to="/" aria-label="ReconRail home"><span className={styles.brandMark} aria-hidden="true">R</span>reconrail<span className={styles.sandbox}>sandbox</span></Link>
      <nav aria-label="Main navigation" className={styles.nav}><NavLink to="/" end>Overview</NavLink><NavLink to="/guide">How it works</NavLink></nav>
      <Link className={styles.workspaceLink} to="/cases">Workspace <Arrow/></Link>
    </header>
    <main id="main" ref={main}><Outlet/></main>
    <footer className={styles.footer}><Link to="/" className={styles.footerBrand}>reconrail</Link><span>Two saved perspectives. Clearer questions.</span><span>Development shell · synthetic example only</span></footer>
  </div>;
}

export function Landing() {
  return <>
    <section className={styles.hero} aria-labelledby="hero-title">
      <p className={styles.eyebrow}>A fresh perspective on payment evidence</p>
      <h1 id="hero-title" tabIndex={-1}>Two stories.<br/>One <em>clearer picture.</em></h1>
      <p className={styles.intro}>A payment. An order. A story worth understanding. Bring both perspectives into focus, and follow each question back to its evidence.</p>
      <div className={styles.actions}><Link className={styles.primary} to="/example">Try the guided example <Arrow/></Link><Link className={styles.secondary} to="/guide">How ReconRail works</Link></div>
      <ul className={styles.assurances}><li>Two saved JSON files</li><li>Sandbox evidence</li><li>No API keys</li></ul>
    </section>
    <EvidencePlayground/>
    <section className={styles.workflow} aria-labelledby="workflow-title"><div><p className={styles.eyebrow}>Follow the evidence</p><h2 id="workflow-title">A little context.<br/>A different perspective.</h2></div>
      <ol><li><span>01</span><h3>Bring both stories</h3><p>A saved provider observation and an independent application expectation.</p></li><li><span>02</span><h3>Review the context</h3><p>Check the exact links, supported flow and declared observation windows.</p></li><li><span>03</span><h3>Understand each finding</h3><p>Inspect what the evidence supports, and what remains unknown.</p></li></ol>
    </section>
    <section className={styles.scope}><span className={styles.scopeIcon} aria-hidden="true">◎</span><div><h2>A clearer view, with clear boundaries.</h2><p>ReconRail compares saved sandbox evidence. A comparison does not establish source authenticity, diagnose a cause or repair a payment. Private investigations and sign-in are not available in this build.</p></div><Link className={styles.secondary} to="/guide">Read the guide <Arrow/></Link></section>
  </>;
}

type Source = 'provider' | 'application';
type Step = 'sources' | 'compare' | 'trace';
const descriptions: Record<Step, {title: string; text: string}> = {
  sources: {title: 'One payment. Two independent observations.', text: 'Inspect the provider projection and the order expectation. These are fixed synthetic illustrations, not uploaded or authenticated evidence.'},
  compare: {title: 'Ask three questions, separately.', text: 'State, currency and amount are distinct checks. Matching values do not establish global financial correctness; a state conclusion also depends on the declared observation context.'},
  trace: {title: 'Every question starts with a source.', text: 'The example explicitly links order_demo to pi_demo, and pi_demo to ch_demo. Exact links support investigation; arrival order and guesses do not.'},
};
export function EvidencePlayground() {
  const [source, setSource] = useState<Source>('provider');
  const [step, setStep] = useState<Step>('sources');
  const [angle, setAngle] = useState(0);
  const [spread, setSpread] = useState(false);
  const [paused, setPaused] = useState(() => {
    try { return localStorage.getItem('rr.motion.paused') === 'true'; }
    catch { return false; }
  });
  const [reduced, setReduced] = useState(false);
  useEffect(() => {
    const media = window.matchMedia('(prefers-reduced-motion: reduce)');
    const update = () => setReduced(media.matches);
    update(); media.addEventListener('change', update);
    return () => media.removeEventListener('change', update);
  }, []);
  const playing = !paused && !reduced;
  const toggleMotion = () => {
    const next = !paused;
    setPaused(next);
    try { localStorage.setItem('rr.motion.paused', String(next)); }
    catch { /* Storage may be blocked; the current page's native control still works. */ }
  };
  return <section className={styles.playground} aria-labelledby="playground-title" data-playing={playing} data-spread={spread}>
    <h2 id="playground-title" className={styles.visuallyHidden}>Explore the synthetic evidence story</h2>
    <div className={styles.scene}>
      <div className={styles.sceneGlow} aria-hidden="true"/>
      <div className={`${styles.artCard} ${styles.providerArt}`} aria-hidden="true"><p>01 / PROVIDER EVIDENCE</p><div className={styles.paymentTile}><span>PAYMENT INTENT</span><strong>pi_demo</strong><span>SAVED · SANDBOX</span><i/></div><h3>The payment story.</h3><p>A saved provider projection</p><span className={styles.miniLink}>pi_demo ↔ ch_demo</span></div>
      <div className={styles.lensGroup} aria-hidden="true"><div className={styles.ring} data-angle={angle % 180}/><div className={styles.lensCenter}><span>⇄</span><p>Comparison lens</p><small>Another perspective</small></div></div>
      <div className={`${styles.artCard} ${styles.applicationArt}`} aria-hidden="true"><p>02 / APPLICATION EVIDENCE</p><div className={styles.receipt}><span>ORDER EXPECTATION</span><strong>order_demo</strong><hr/><b>1000</b><span>USD minor units</span><hr/><span>awaiting_payment</span></div><h3>The order story.</h3><p>An independent expectation</p></div>
      <div className={styles.chargeToken} aria-hidden="true"><span>↔</span><div><small>EXPLICIT CHARGE LINK</small><p>ch_demo ↔ pi_demo</p></div></div>
      <div className={styles.satellites} aria-hidden="true"><span><i>01 / STATE</i>Review context</span><span><i>02 / CURRENCY</i>USD ↔ USD</span><span><i>03 / AMOUNT</i>1000 ↔ 1000</span></div>
    </div>
    <p className={styles.syntheticLabel}>A fixed synthetic story · no live payments · no analysis has run</p>
    <div className={styles.playControls}><div role="group" aria-label="Example perspective">{(['sources','compare','trace'] as Step[]).map((s,i) => <button key={s} aria-pressed={step===s} onClick={() => setStep(s)}><span>0{i+1}</span> {s[0].toUpperCase()+s.slice(1)}</button>)}</div>
      <div className={styles.sceneControls}><button onClick={() => setSpread(s=>!s)} aria-pressed={spread}>◇ {spread?'Gather':'Spread'} evidence</button><button onClick={() => setAngle(a=>a+45)}>↻ Turn lens</button><button onClick={() => {setAngle(0);setSpread(false);setStep('sources');setSource('provider');}}>Reset</button><button onClick={toggleMotion} disabled={reduced}>{reduced?'Reduced motion':paused?'Resume motion':'Pause motion'}</button></div>
    </div>
    <div className={styles.readout}><div className={styles.readoutIntro}><p className={styles.eyebrow}>{step} / synthetic illustration</p><h2>{descriptions[step].title}</h2><p>{descriptions[step].text}</p></div>
      <div className={styles.sourceDetails}><div role="group" aria-label="Inspect a saved source"><button aria-pressed={source==='provider'} onClick={()=>setSource('provider')}>Provider source</button><button aria-pressed={source==='application'} onClick={()=>setSource('application')}>Application source</button></div>
        <div aria-live="polite" aria-atomic="true"><h3>{source==='provider'?'Saved provider projection':'Independent order expectation'}</h3><dl><div><dt>{source==='provider'?'PaymentIntent':'Order'}</dt><dd>{source==='provider'?'pi_demo':'order_demo'}</dd></div><div><dt>Explicit link</dt><dd>{source==='provider'?'latest Charge → ch_demo':'PaymentIntent → pi_demo'}</dd></div><div><dt>Illustrated amount</dt><dd>1000 USD minor units</dd></div></dl><p className={styles.detailNote}>Illustration only. Observation bounds and the full supported capture profile have not been validated.</p></div>
      </div>
    </div>
  </section>;
}

export function Guide() {
  return <article className={styles.document}><p className={styles.eyebrow}>The ReconRail guide</p><h1 tabIndex={-1}>Start with the evidence.</h1><p className={styles.lead}>ReconRail is designed to compare two saved sandbox observations against explicit expectations.</p><ol className={styles.guideSteps}><li><h2>Save two independent perspectives</h2><p>One provider artifact and one application-order artifact. The supported contract is strict JSON with declared versions, identities and observation context. Raw provider responses are not accepted as-is.</p></li><li><h2>Review before drawing a conclusion</h2><p>Check the order-to-PaymentIntent and latest-Charge links. Missing or ambiguous evidence must remain visible. Observation time is different from upload time.</p></li><li><h2>Keep the questions separate</h2><p>State, amount and currency each have their own eligibility and limitations. An individually consistent check does not mean a payment is repaired or globally correct.</p></li></ol><aside className={styles.notice}><h2>This build is a shell</h2><p>Sign-in, uploads, validation, analysis, saved history, deletion and closure are not connected yet. The guided example uses fixed synthetic values; it does not run the financial evaluator.</p></aside><Link className={styles.primary} to="/example">Explore the example <Arrow/></Link></article>;
}
export function Example() {
  return <><header className={styles.exampleHeading}><p className={styles.eyebrow}>Look closer</p><h1 tabIndex={-1}>Two perspectives, side by side.</h1><p>Explore a fixed synthetic illustration. Select a source, change perspective or turn the comparison lens.</p></header><EvidencePlayground/></>;
}
export function Unavailable() {
  return <section className={styles.document}><p className={styles.eyebrow}>Development shell</p><h1 tabIndex={-1}>Your workspace is coming next.</h1><p className={styles.lead}>Private investigations are not available in this build.</p><p>Authentication, evidence intake and account lifecycle controls are not implemented yet. No account is signed in, no evidence is stored, and this page cannot accept files or credentials.</p><div className={styles.actions}><Link className={styles.primary} to="/example">Explore the synthetic example <Arrow/></Link><Link className={styles.secondary} to="/guide">Read the guide</Link></div></section>;
}
export function MissingPage() {
  return <section className={styles.document}><p className={styles.eyebrow}>Page unavailable</p><h1 tabIndex={-1}>Let’s find your way back.</h1><p>This page is not part of the current shell.</p><Link className={styles.primary} to="/">Return to overview <Arrow/></Link></section>;
}
