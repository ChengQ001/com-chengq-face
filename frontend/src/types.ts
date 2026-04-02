export type Point2 = { x: number; y: number }
export type BBox = { x: number; y: number; width: number; height: number }

export type DetectFace = {
  bbox: BBox
  score: number
  landmarks?: Point2[] | null
  cropImageBase64?: string | null
}

export type DetectResponse = {
  imageWidth: number
  imageHeight: number
  faces: DetectFace[]
}

