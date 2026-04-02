package com.chengq.face.face.djl;

import java.util.Arrays;

/**
 * Port of {@code cv::FaceRecognizerSFImpl::getSimilarityTransformMatrix} (OpenCV 4.x) for 5-point
 * similarity alignment to the reference used by YuNet + SFace.
 */
public final class OpenCvSimilarityTransform {

  private static final float[][] DST =
      new float[][] {
        {38.2946f, 51.6963f},
        {73.5318f, 51.5014f},
        {56.0252f, 71.7366f},
        {41.5493f, 92.3655f},
        {70.7299f, 92.2041f}
      };

  private OpenCvSimilarityTransform() {}

  /**
   * 2×3 matrix row-major: [m00 m01 m02; m10 m11 m12] mapping destination (x,y) to source
   * coordinates for bilinear sampling: sx = m00*x + m01*y + m02, sy = m10*x + m11*y + m12.
   */
  public static double[] getMatrix(float[][] srcFiveByTwo) {
    if (srcFiveByTwo.length != 5 || srcFiveByTwo[0].length != 2) {
      throw new IllegalArgumentException("Need 5 landmarks × 2 coords");
    }
    float[][] src = srcFiveByTwo;
    float avg0 = (src[0][0] + src[1][0] + src[2][0] + src[3][0] + src[4][0]) / 5f;
    float avg1 = (src[0][1] + src[1][1] + src[2][1] + src[3][1] + src[4][1]) / 5f;
    float[] srcMean = new float[] {avg0, avg1};
    float[] dstMean = new float[] {56.0262f, 71.9008f};

    float[][] srcDemean = new float[5][2];
    float[][] dstDemean = new float[5][2];
    for (int i = 0; i < 2; i++) {
      for (int j = 0; j < 5; j++) {
        srcDemean[j][i] = src[j][i] - srcMean[i];
        dstDemean[j][i] = DST[j][i] - dstMean[i];
      }
    }

    double A00 = 0, A01 = 0, A10 = 0, A11 = 0;
    for (int i = 0; i < 5; i++) {
      A00 += dstDemean[i][0] * srcDemean[i][0];
    }
    A00 /= 5;
    for (int i = 0; i < 5; i++) {
      A01 += dstDemean[i][0] * srcDemean[i][1];
    }
    A01 /= 5;
    for (int i = 0; i < 5; i++) {
      A10 += dstDemean[i][1] * srcDemean[i][0];
    }
    A10 /= 5;
    for (int i = 0; i < 5; i++) {
      A11 += dstDemean[i][1] * srcDemean[i][1];
    }
    A11 /= 5;

    double[] d = new double[] {1.0, 1.0};
    double detA = A00 * A11 - A01 * A10;
    if (detA < 0) {
      d[1] = -1;
    }

    double[][] u = new double[2][2];
    double[] s = new double[2];
    double[][] vt = new double[2][2];
    svd22(A00, A01, A10, A11, u, s, vt);

    double smax = Math.max(s[0], s[1]);
    double tol = smax * 2 * Float.MIN_VALUE;
    int rank = 0;
    if (s[0] > tol) {
      rank++;
    }
    if (s[1] > tol) {
      rank++;
    }

    double[][] arrU = new double[][] {{u[0][0], u[0][1]}, {u[1][0], u[1][1]}};
    double[][] arrVt = new double[][] {{vt[0][0], vt[0][1]}, {vt[1][0], vt[1][1]}};

    double detU = arrU[0][0] * arrU[1][1] - arrU[0][1] * arrU[1][0];
    double detVt = arrVt[0][0] * arrVt[1][1] - arrVt[0][1] * arrVt[1][0];

    double[][] T = new double[][] {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};

    if (rank == 1) {
      if ((detU * detVt) > 0) {
        double[][] uvt = mul22(u, vt);
        T[0][0] = uvt[0][0];
        T[0][1] = uvt[0][1];
        T[1][0] = uvt[1][0];
        T[1][1] = uvt[1][1];
      } else {
        double temp = d[1];
        d[1] = -1;
        double[][] D = new double[][] {{d[0], 0}, {0, d[1]}};
        double[][] uDvt = mul22(mul22(u, D), vt);
        T[0][0] = uDvt[0][0];
        T[0][1] = uDvt[0][1];
        T[1][0] = uDvt[1][0];
        T[1][1] = uDvt[1][1];
        d[1] = temp;
      }
    } else {
      double[][] D = new double[][] {{d[0], 0}, {0, d[1]}};
      double[][] uDvt = mul22(mul22(u, D), vt);
      T[0][0] = uDvt[0][0];
      T[0][1] = uDvt[0][1];
      T[1][0] = uDvt[1][0];
      T[1][1] = uDvt[1][1];
    }

    double var1 = 0, var2 = 0;
    for (int i = 0; i < 5; i++) {
      var1 += srcDemean[i][0] * srcDemean[i][0];
    }
    var1 /= 5;
    for (int i = 0; i < 5; i++) {
      var2 += srcDemean[i][1] * srcDemean[i][1];
    }
    var2 /= 5;

    double scale = 1.0 / (var1 + var2) * (s[0] * d[0] + s[1] * d[1]);
    double TS0 = T[0][0] * srcMean[0] + T[0][1] * srcMean[1];
    double TS1 = T[1][0] * srcMean[0] + T[1][1] * srcMean[1];
    T[0][2] = dstMean[0] - scale * TS0;
    T[1][2] = dstMean[1] - scale * TS1;
    T[0][0] *= scale;
    T[0][1] *= scale;
    T[1][0] *= scale;
    T[1][1] *= scale;

    return new double[] {T[0][0], T[0][1], T[0][2], T[1][0], T[1][1], T[1][2]};
  }

  public static float[][] landmarksFromDetectorRow(float[] row15) {
    float[][] src = new float[5][2];
    for (int row = 0; row < 5; row++) {
      src[row][0] = row15[4 + row * 2];
      src[row][1] = row15[4 + row * 2 + 1];
    }
    return src;
  }

  private static double[][] mul22(double[][] a, double[][] b) {
    return new double[][] {
      {a[0][0] * b[0][0] + a[0][1] * b[1][0], a[0][0] * b[0][1] + a[0][1] * b[1][1]},
      {a[1][0] * b[0][0] + a[1][1] * b[1][0], a[1][0] * b[0][1] + a[1][1] * b[1][1]}
    };
  }

  /**
   * SVD of 2×2 matrix A = U Σ Vᵀ using eigendecomposition of AᵀA (same algebra as OpenCV for
   * full-rank real matrices).
   */
  private static void svd22(
      double a,
      double b,
      double c,
      double d,
      double[][] uOut,
      double[] sOut,
      double[][] vtOut) {
    double p = a * a + c * c;
    double q = a * b + c * d;
    double r = b * b + d * d;
    double halfTrace = (p + r) / 2;
    double disc = halfTrace * halfTrace - (p * r - q * q);
    if (disc < 0) {
      disc = 0;
    }
    double root = Math.sqrt(disc);
    double l1 = halfTrace + root;
    double l2 = halfTrace - root;
    double s0 = Math.sqrt(Math.max(l1, 0));
    double s1 = Math.sqrt(Math.max(l2, 0));
    sOut[0] = s0;
    sOut[1] = s1;

    double v00, v01, v10, v11;
    if (Math.abs(q) < 1e-12 && Math.abs(p - l1) < 1e-12) {
      v00 = 1;
      v01 = 0;
      v10 = 0;
      v11 = 1;
    } else {
      double nx = q;
      double ny = l1 - p;
      double norm = Math.hypot(nx, ny);
      if (norm < 1e-12) {
        nx = l1 - r;
        ny = q;
        norm = Math.hypot(nx, ny);
      }
      if (norm < 1e-12) {
        v00 = 1;
        v01 = 0;
        v10 = 0;
        v11 = 1;
      } else {
        v00 = nx / norm;
        v01 = ny / norm;
        v10 = -v01;
        v11 = v00;
      }
    }

    double[][] v = new double[][] {{v00, v01}, {v10, v11}};
    double invS0 = s0 > 1e-12 ? 1 / s0 : 0;
    double invS1 = s1 > 1e-12 ? 1 / s1 : 0;
    double[][] av = new double[2][2];
    av[0][0] = a * v[0][0] + b * v[1][0];
    av[0][1] = a * v[0][1] + b * v[1][1];
    av[1][0] = c * v[0][0] + d * v[1][0];
    av[1][1] = c * v[0][1] + d * v[1][1];

    double[][] u = new double[][] {
      {av[0][0] * invS0, av[0][1] * invS1},
      {av[1][0] * invS0, av[1][1] * invS1}
    };
    // Orthonormalize U (columns)
    double n0 = Math.hypot(u[0][0], u[1][0]);
    double n1 = Math.hypot(u[0][1], u[1][1]);
    if (n0 > 1e-12) {
      u[0][0] /= n0;
      u[1][0] /= n0;
    }
    if (n1 > 1e-12) {
      u[0][1] /= n1;
      u[1][1] /= n1;
    }

    uOut[0][0] = u[0][0];
    uOut[0][1] = u[0][1];
    uOut[1][0] = u[1][0];
    uOut[1][1] = u[1][1];
    // Vᵀ for A = U Σ Vᵀ (columns of v are eigenvectors of AᵀA)
    vtOut[0][0] = v[0][0];
    vtOut[0][1] = v[1][0];
    vtOut[1][0] = v[0][1];
    vtOut[1][1] = v[1][1];

    if (!Double.isFinite(sOut[0]) || !Double.isFinite(sOut[1])) {
      Arrays.fill(sOut, 0);
      uOut[0][0] = uOut[1][1] = 1;
      uOut[0][1] = uOut[1][0] = 0;
      vtOut[0][0] = vtOut[1][1] = 1;
      vtOut[0][1] = vtOut[1][0] = 0;
    }
  }
}
