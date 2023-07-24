type MergeOptions = {
  maxWidth?: number;
};

type MergeResult = {
  path: string;
  width: number;
  height: number;
};

declare class RNMergeImages {
  static merge(
    imagePaths: string[],
    options?: MergeOptions
  ): Promise<MergeResult>;
}

export default RNMergeImages;
