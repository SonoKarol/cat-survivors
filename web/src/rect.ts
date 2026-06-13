/** Rettangolo con test di contenimento, come java.awt.Rectangle (low incluso, high escluso). */
export class Rect {
  constructor(
    public x: number,
    public y: number,
    public width: number,
    public height: number,
  ) {}

  contains(px: number, py: number): boolean {
    return px >= this.x && py >= this.y && px < this.x + this.width && py < this.y + this.height;
  }
}
