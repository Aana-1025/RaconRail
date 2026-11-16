import { expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { canonical, type Layouts } from './canonical-codec';
const manifest=JSON.parse(readFileSync('../docs/04-apis-integrations/codec-manifest.json','utf8')) as { layouts: Layouts };
const vectors=JSON.parse(readFileSync('../docs/04-apis-integrations/codec-golden-vectors.json','utf8')) as { vectors: { label: string; domain: string; layout: string; input: unknown; canonicalHex: string }[] };
for(const v of vectors.vectors) it(`canonical parity: ${v.label}`,()=>expect(Buffer.from(canonical(v.domain,v.layout,v.input,manifest.layouts)).toString('hex')).toBe(v.canonicalHex));
it('rejects unknown fields',()=>expect(()=>canonical('rr.config.v1','PurposeContext',{secret:'never echo'},manifest.layouts)).toThrow('INVALID_CODEC'));
