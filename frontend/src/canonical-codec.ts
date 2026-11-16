type Field = { name: string; type: string; required: boolean; nullable: boolean };
export type Layouts = Record<string, Field[]>;
const invalid = (): never => { throw new Error('INVALID_CODEC'); };
function text(value: unknown): Uint8Array {
  if (typeof value !== 'string' || value.includes('\0')) return invalid();
  for (let i=0;i<value.length;i++) {
    const c=value.charCodeAt(i);
    if (c>=0xdc00 && c<=0xdfff) return invalid();
    if (c>=0xd800 && c<=0xdbff) { const next=value.charCodeAt(++i); if (!(next>=0xdc00 && next<=0xdfff)) return invalid(); }
  }
  const bytes=new TextEncoder().encode(value); if(bytes.length>65536) return invalid(); return bytes;
}
/** Manifest ordered canonical bytes; money is never converted to Number. */
export function canonical(domain: string, layout: string, value: unknown, layouts: Layouts): Uint8Array {
  if (!['rr.command.v1','rr.review.v1','rr.config.v1','rr.journal.intent.v1','rr.journal.checkpoint.v1','rr.semantic.v1'].includes(domain)) return invalid();
  const output: number[]=[];
  const put=(bytes: Uint8Array) => { if (output.length+bytes.length>67108864) return invalid(); for(const b of bytes) output.push(b); };
  const u32=(n: number) => {const b=new Uint8Array(4);new DataView(b.buffer).setUint32(0,n);put(b);};
  const i64=(n: bigint) => {if(n<-(1n<<63n)||n>=(1n<<63n)) return invalid();const b=new Uint8Array(8);new DataView(b.buffer).setBigInt64(0,n);put(b);};
  const sized=(b: Uint8Array) => {if(b.length>65536)return invalid();u32(b.length);put(b);};
  function object(name: string, v: unknown, depth: number) {
    if(depth>16 || typeof v!=='object'||v===null||Array.isArray(v)||!layouts[name]) return invalid();
    const record=v as Record<string,unknown>, fields=layouts[name];
    if(Object.keys(record).some(k=>!fields.some(f=>f.name===k)))return invalid();
    u32(fields.length);
    for(const f of fields){
      if(!Object.hasOwn(record,f.name)){if(f.required)return invalid();output.push(0);}
      else if(record[f.name]===null){if(!f.nullable)return invalid();output.push(1);}
      else{output.push(2);typed(f.type,record[f.name],depth+1);}
    }
  }
  function typed(type: string,v: unknown,depth: number){
    if(depth>16)return invalid();
    if(type.startsWith('object:')){output.push(8);object(type.slice(7),v,depth);return;}
    if(type.startsWith('list:')){if(!Array.isArray(v)||v.length>(type==='list:object:SemanticFinding'?60000:10000))return invalid();output.push(7);u32(v.length);for(const item of v){if(item===null)return invalid();typed(type.slice(5),item,depth+1);}return;}
    switch(type){
      case 'text':output.push(1);sized(text(v));break;
      case 'integer':if(typeof v!=='string'||!/^(?:0|-?[1-9][0-9]{0,37})$/.test(v)||/\s/.test(v))return invalid();output.push(2);sized(text(v));break;
      case 'boolean':if(typeof v!=='boolean')return invalid();output.push(5,v?1:0);break;
      case 'uuid':if(typeof v!=='string'||v.length!==36||!/^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/.test(v))return invalid();output.push(3);put(Uint8Array.from(v.replaceAll('-','').match(/../g)!,h=>parseInt(h,16)));break;
      case 'hexbytes':if(typeof v!=='string'||v.length>131072||v.length%2||!/^[0-9a-f]*$/.test(v)||/\s/.test(v))return invalid();output.push(6);sized(Uint8Array.from(v.match(/../g)??[],h=>parseInt(h,16)));break;
      case 'timestamp':{
        if(typeof v!=='string'||v.startsWith('0000')||!/^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-5][0-9](\.[0-9]{1,6})?Z$/.test(v)||/\s/.test(v))return invalid();
        const base=v.slice(0,19)+'Z',ms=Date.parse(base);if(!Number.isFinite(ms)||new Date(ms).toISOString().slice(0,19)!==v.slice(0,19))return invalid();
        const fraction=v.includes('.')?v.split('.')[1].slice(0,-1):'';output.push(4);i64(BigInt(ms)*1000n+BigInt(fraction.padEnd(6,'0')));break;
      }
      default:return invalid();
    }
  }
  put(text(domain));output.push(0);u32(1);object(layout,value,0);return Uint8Array.from(output);
}
