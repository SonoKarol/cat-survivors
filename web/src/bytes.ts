// Sostituiscono DataOutputStream/DataInputStream (big-endian come Java).
// writeUTF/readUTF usano un prefisso di lunghezza a 2 byte + UTF-8, come Java.

const ENC = new TextEncoder();
const DEC = new TextDecoder();

export class ByteWriter {
  private buf = new ArrayBuffer(4096);
  private view = new DataView(this.buf);
  private u8 = new Uint8Array(this.buf);
  private pos = 0;

  private ensure(n: number): void {
    if (this.pos + n <= this.buf.byteLength) return;
    let cap = this.buf.byteLength * 2;
    while (cap < this.pos + n) cap *= 2;
    const nb = new ArrayBuffer(cap);
    new Uint8Array(nb).set(this.u8);
    this.buf = nb;
    this.view = new DataView(nb);
    this.u8 = new Uint8Array(nb);
  }

  byte(v: number): this { this.ensure(1); this.view.setUint8(this.pos, v & 0xff); this.pos += 1; return this; }
  short(v: number): this { this.ensure(2); this.view.setInt16(this.pos, v); this.pos += 2; return this; }
  int(v: number): this { this.ensure(4); this.view.setInt32(this.pos, v); this.pos += 4; return this; }
  float(v: number): this { this.ensure(4); this.view.setFloat32(this.pos, v); this.pos += 4; return this; }
  double(v: number): this { this.ensure(8); this.view.setFloat64(this.pos, v); this.pos += 8; return this; }
  bool(v: boolean): this { return this.byte(v ? 1 : 0); }

  utf(s: string): this {
    const b = ENC.encode(s);
    this.short(b.length);
    this.ensure(b.length);
    this.u8.set(b, this.pos);
    this.pos += b.length;
    return this;
  }

  raw(b: Uint8Array): this {
    this.ensure(b.length);
    this.u8.set(b, this.pos);
    this.pos += b.length;
    return this;
  }

  toBytes(): Uint8Array { return this.u8.slice(0, this.pos); }
}

export class ByteReader {
  private view: DataView;
  private u8: Uint8Array;
  private pos = 0;

  constructor(data: Uint8Array) {
    this.u8 = data;
    this.view = new DataView(data.buffer, data.byteOffset, data.byteLength);
  }

  byte(): number { const v = this.view.getInt8(this.pos); this.pos += 1; return v; }
  ubyte(): number { const v = this.view.getUint8(this.pos); this.pos += 1; return v; }
  short(): number { const v = this.view.getInt16(this.pos); this.pos += 2; return v; }
  ushort(): number { const v = this.view.getUint16(this.pos); this.pos += 2; return v; }
  int(): number { const v = this.view.getInt32(this.pos); this.pos += 4; return v; }
  float(): number { const v = this.view.getFloat32(this.pos); this.pos += 4; return v; }
  double(): number { const v = this.view.getFloat64(this.pos); this.pos += 8; return v; }
  bool(): boolean { return this.ubyte() !== 0; }

  utf(): string {
    const len = this.ushort();
    const s = DEC.decode(this.u8.subarray(this.pos, this.pos + len));
    this.pos += len;
    return s;
  }

  remaining(): Uint8Array { return this.u8.subarray(this.pos); }
}
