import { expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { canonical, type Layouts } from './canonical-codec';
const manifest=JSON.parse(readFileSync('../docs/04-apis-integrations/codec-manifest.json','utf8')) as { layouts: Layouts };
const vectors=JSON.parse(readFileSync('../docs/04-apis-integrations/codec-golden-vectors.json','utf8')) as { vectors: { label: string; domain: string; layout: string; input: unknown; canonicalHex: string }[] };
for(const v of vectors.vectors) it(`canonical parity: ${v.label}`,()=>expect(Buffer.from(canonical(v.domain,v.layout,v.input,manifest.layouts)).toString('hex')).toBe(v.canonicalHex));
it('rejects unknown fields',()=>expect(()=>canonical('rr.config.v1','PurposeContext',{secret:'never echo'},manifest.layouts)).toThrow('INVALID_CODEC'));

it('encodes more than 10000 semantic findings without relaxing artifact limits',()=>{
  const finding={ruleId:'DOM-F-001',classification:'MORE_EVIDENCE_NEEDED',subjectKind:'ORDER',subjectId:'order',explanationCode:'MISSING_EXPLICIT_BINDING',qualifiers:[],observedValues:{}};
  const input={inputCounts:{paymentIntents:'0',charges:'0',orders:'4000'},checks:Object.fromEntries(['state','currency','amount'].map(k=>[k,{eligible:'0',consistent:'0',discrepancy:'0',moreEvidenceNeeded:'4000',unsupported:'0',ambiguous:'0'}])),qualifiers:[],findings:Array.from({length:12000},()=>finding)};
  expect(canonical('rr.semantic.v1','SemanticResult',input,manifest.layouts).length).toBeGreaterThan(10000);
});
