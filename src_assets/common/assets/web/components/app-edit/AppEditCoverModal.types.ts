/** Public data contract shared by cover search and selection. */
export interface CoverCandidate {
  name: string;
  key: string;
  url: string;
  saveUrl: string;
  /**
   * Presentation hint. The source file keeps its native dimensions; `wide`
   * candidates are cropped into the 16:9 artwork frame, not resized claims.
   */
  aspect?: 'portrait' | 'wide';
  source?: 'igdb' | 'steamgriddb';
}
