package grondag.canvas.chunk.occlusion;

import grondag.canvas.render.CanvasWorldRenderer;

// Some elements are adapted from content found at
// https://fgiesen.wordpress.com/2013/02/17/optimizing-sw-occlusion-culling-index/
// by Fabian “ryg” Giesen. That content is in the public domain.

public class TerrainOccluder extends ClippingTerrainOccluder  {
	private int aMid0;
	private int bMid0;
	private int aMid1;
	private int bMid1;
	private int aMid2;
	private int bMid2;
	private int abMid0;
	private int abMid1;
	private int abMid2;

	private int aTop0;
	private int bTop0;
	private int aTop1;
	private int bTop1;
	private int aTop2;
	private int bTop2;
	private int abTop0;
	private int abTop1;
	private int abTop2;

	@Override
	protected void prepareTriScan() {
		super.prepareTriScan();

		aMid0 = a0 * MID_BIN_PIXEL_DIAMETER - a0;
		bMid0 = b0 * MID_BIN_PIXEL_DIAMETER - b0;
		aMid1 = a1 * MID_BIN_PIXEL_DIAMETER - a1;
		bMid1 = b1 * MID_BIN_PIXEL_DIAMETER - b1;
		aMid2 = a2 * MID_BIN_PIXEL_DIAMETER - a2;
		bMid2 = b2 * MID_BIN_PIXEL_DIAMETER - b2;
		abMid0 = aMid0 + bMid0;
		abMid1 = aMid1 + bMid1;
		abMid2 = aMid2 + bMid2;

		aTop0 = a0 * TOP_BIN_PIXEL_DIAMETER - a0;
		bTop0 = b0 * TOP_BIN_PIXEL_DIAMETER - b0;
		aTop1 = a1 * TOP_BIN_PIXEL_DIAMETER - a1;
		bTop1 = b1 * TOP_BIN_PIXEL_DIAMETER - b1;
		aTop2 = a2 * TOP_BIN_PIXEL_DIAMETER - a2;
		bTop2 = b2 * TOP_BIN_PIXEL_DIAMETER - b2;
		abTop0 = aTop0 + bTop0;
		abTop1 = aTop1 + bTop1;
		abTop2 = aTop2 + bTop2;
	}

	@Override
	protected void drawTri(ProjectionVector4f v0, ProjectionVector4f v1, ProjectionVector4f v2) {
		if (!prepareTriBounds(v0, v1, v2)) {
			return;
		}

		if (triNeedsClipped()) {
			drawClippedLowX(v0, v1, v2);
			return;
		}

		prepareTriScan();

		final int bx0 = minPixelX >> TOP_AXIS_SHIFT;
		final int bx1 = maxPixelX >> TOP_AXIS_SHIFT;
		final int by0 = minPixelY >> TOP_AXIS_SHIFT;
		final int by1 = maxPixelY >> TOP_AXIS_SHIFT;

		if (bx0 == bx1 && by0 == by1) {
			drawTriTop(bx0, by0);
		} else {
			for (int by = by0; by <= by1; by++) {
				for (int bx = bx0; bx <= bx1; bx++) {
					drawTriTop(bx, by);
				}
			}
		}
	}

	private void drawTriTop(final int topX, final int topY) {
		final int index = topIndex(topX, topY);
		long word = topBins[index];

		if (word == -1L) {
			// if bin fully occluded nothing to do
			return;
		}

		final int binOriginX = topX << TOP_AXIS_SHIFT;
		final int binOriginY = topY << TOP_AXIS_SHIFT;
		final int minPixelX = this.minPixelX;
		final int minPixelY = this.minPixelY;

		final int minX = minPixelX < binOriginX ? binOriginX : minPixelX;
		final int minY = minPixelY < binOriginY ? binOriginY : minPixelY;
		final int maxX = Math.min(maxPixelX, binOriginX + TOP_BIN_PIXEL_DIAMETER - 1);
		final int maxY = Math.min(maxPixelY, binOriginY + TOP_BIN_PIXEL_DIAMETER - 1);

		long coverage = coverageMask((minX >> MID_AXIS_SHIFT) & 7, (minY >> MID_AXIS_SHIFT) & 7,
				(maxX >> MID_AXIS_SHIFT) & 7, (maxY >> MID_AXIS_SHIFT) & 7);

		// if filling whole bin then do it quick
		if ((coverage & CORNER_COVERAGE_MASK) == CORNER_COVERAGE_MASK && isTriTopCovered(minX,  minY)) {
			topBins[index] = -1;

			if (ENABLE_RASTER_OUTPUT) {
				fillTopBinChildren(topX, topY);
			}

			return;
		}

		coverage &= ~word;

		if (coverage == 0) {
			return;
		}

		final int baseX = topX << BIN_AXIS_SHIFT;
		final int baseY = topY << BIN_AXIS_SHIFT;

		long mask = 1;

		for (int n = 0; n < 64; ++n) {
			if ((mask & coverage) != 0) {
				CanvasWorldRenderer.innerTimer.start();
				final boolean mid = drawTriMid(baseX + (n & 7), baseY + (n >> 3));
				CanvasWorldRenderer.innerTimer.stop();

				if (mid) {
					word |= mask;
				}
			}

			mask <<= 1;
		}

		topBins[index] = word;
	}

	private boolean isTriTopCovered(int minX, int minY) {
		final int dx = minX - minPixelX;
		final int dy = minY - minPixelY;
		final int w0_row = wOrigin0 + dx * a0 + dy * b0;
		final int w1_row = wOrigin1 + dx * a1 + dy * b1;
		final int w2_row = wOrigin2 + dx * a2 + dy * b2;

		if ((w0_row | w1_row | w2_row
				| (w0_row + aTop0) | (w1_row + aTop1) | (w2_row + aTop2)
				| (w0_row + bTop0) | (w1_row + bTop1) | (w2_row + bTop2)
				| (w0_row + abTop0) | (w1_row + abTop1) | (w2_row + abTop2)) >= 0) {
			return true;
		} else {
			return false;
		}
	}

	private void fillTopBinChildren(final int topX, final int topY) {
		final int midX0 = topX << 3;
		final int midY0 = topY << 3;
		final int midX1 = midX0 + 7;
		final int midY1 = midY0 + 7;

		for (int midY = midY0; midY <= midY1; midY++) {
			for (int midX = midX0; midX <= midX1; midX++) {
				final int index = midIndex(midX, midY);

				if (midBins[index] != -1L) {
					midBins[index] = -1L;
					fillMidBinChildren(midX, midY);
				}
			}
		}
	}

	private void fillMidBinChildren(final int midX, final int midY) {
		final int lowX0 = midX << 3;
		final int lowY0 = midY << 3;
		final int lowX1 = lowX0 + 7;
		final int lowY1 = lowY0 + 7;

		for (int lowY = lowY0; lowY <= lowY1; lowY++) {
			for (int lowX = lowX0; lowX <= lowX1; lowX++) {
				lowBins[lowIndex(lowX, lowY)] = -1L;
			}
		}
	}

	/**
	 * Returns true when bin fully occluded
	 */
	private boolean drawTriMid(final int midX, final int midY) {
		final int index = midIndex(midX, midY);

		final int binOriginX = midX << MID_AXIS_SHIFT;
		final int binOriginY = midY << MID_AXIS_SHIFT;
		final int minPixelX = this.minPixelX;
		final int minPixelY = this.minPixelY;

		final int minX = minPixelX < binOriginX ? binOriginX : minPixelX;
		final int minY = minPixelY < binOriginY ? binOriginY : minPixelY;
		final int maxX = Math.min(maxPixelX, binOriginX + MID_BIN_PIXEL_DIAMETER - 1);
		final int maxY = Math.min(maxPixelY, binOriginY + MID_BIN_PIXEL_DIAMETER - 1);

		final int lowX0 = minX >> LOW_AXIS_SHIFT;
		final int lowX1 = maxX >> LOW_AXIS_SHIFT;
		final int lowY0 = minY >> LOW_AXIS_SHIFT;
		final int lowY1 = (maxY >> LOW_AXIS_SHIFT); // parens stop eclipse formatter from freaking

		long word = midBins[index];

		if (lowX0 == lowX1)  {
			if (lowY0 == lowY1) {
				// single  bin
				final long mask = pixelMask(lowX0, lowY0);

				if ((word & mask) != 0 && drawTriLow(lowX0, lowY0)) {
					word |= mask;
					midBins[index] = word;
					return word == -1;
				} else {
					return false;
				}
			} else {
				// single column
				long mask = pixelMask(lowX0, lowY0);

				for (int y = lowY0; y <= lowY1; ++y) {
					if ((word & mask) == 0 && drawTriLow(lowX0, y)) {
						word |= mask;
					}

					mask  <<= 8;
				}

				midBins[index] = word;
				return word == -1;
			}

		} else if (lowY0 == lowY1) {
			// single row
			long mask = pixelMask(lowX0, lowY0);

			for (int x = lowX0; x <= lowX1; ++x) {
				if ((word & mask) == 0 && drawTriLow(x, lowY0)) {
					word |= mask;
				}

				mask  <<= 1;
			}

			midBins[index] = word;
			return word == -1;
		}

		long coverage = coverageMask(lowX0 & 7, lowY0 & 7, lowX1 & 7, lowY1 & 7);

		// optimize whole bin case
		if ((coverage & CORNER_COVERAGE_MASK) == CORNER_COVERAGE_MASK && isTriMidCovered(minX,  minY)) {
			midBins[index] = -1;

			if (ENABLE_RASTER_OUTPUT) {
				fillMidBinChildren(midX, midY);
			}

			return true;
		}

		coverage &= ~word;

		if (coverage == 0) {
			return false;
		}

		final int baseX = midX << BIN_AXIS_SHIFT;
		int baseY = midY << BIN_AXIS_SHIFT;


		for (int y = 0; y < 8; y++) {
			final int bits = (int) coverage & 0xFF;
			int setBits = 0;

			switch (bits & 0xF) {
			case 0b0000:
				break;

			case 0b0001:
				if (drawTriLow(baseX + 0, baseY)) setBits |= 1;
				break;

			case 0b0010:
				if (drawTriLow(baseX + 1, baseY)) setBits |= 2;
				break;

			case 0b0011:
				if (drawTriLow(baseX + 0, baseY)) setBits |= 1;
				if (drawTriLow(baseX + 1, baseY)) setBits |= 2;
				break;

			case 0b0100:
				if (drawTriLow(baseX + 2, baseY)) setBits |= 4;
				break;

			case 0b0101:
				if (drawTriLow(baseX + 0, baseY)) setBits |= 1;
				if (drawTriLow(baseX + 2, baseY)) setBits |= 4;
				break;

			case 0b0110:
				if (drawTriLow(baseX + 1, baseY)) setBits |= 2;
				if (drawTriLow(baseX + 2, baseY)) setBits |= 4;
				break;

			case 0b0111:
				if (drawTriLow(baseX + 0, baseY)) setBits |= 1;
				if (drawTriLow(baseX + 1, baseY)) setBits |= 2;
				if (drawTriLow(baseX + 2, baseY)) setBits |= 4;
				break;

			case 0b1000:
				if (drawTriLow(baseX + 3, baseY)) setBits |= 8;
				break;

			case 0b1001:
				if (drawTriLow(baseX + 0, baseY)) setBits |= 1;
				if (drawTriLow(baseX + 3, baseY)) setBits |= 8;
				break;

			case 0b1010:
				if (drawTriLow(baseX + 1, baseY)) setBits |= 2;
				if (drawTriLow(baseX + 3, baseY)) setBits |= 8;
				break;

			case 0b1011:
				if (drawTriLow(baseX + 0, baseY)) setBits |= 1;
				if (drawTriLow(baseX + 1, baseY)) setBits |= 2;
				if (drawTriLow(baseX + 3, baseY)) setBits |= 8;
				break;

			case 0b1100:
				if (drawTriLow(baseX + 2, baseY)) setBits |= 4;
				if (drawTriLow(baseX + 3, baseY)) setBits |= 8;
				break;

			case 0b1101:
				if (drawTriLow(baseX + 0, baseY)) setBits |= 1;
				if (drawTriLow(baseX + 2, baseY)) setBits |= 4;
				if (drawTriLow(baseX + 3, baseY)) setBits |= 8;
				break;

			case 0b1110:
				if (drawTriLow(baseX + 1, baseY)) setBits |= 2;
				if (drawTriLow(baseX + 2, baseY)) setBits |= 4;
				if (drawTriLow(baseX + 3, baseY)) setBits |= 8;
				break;

			case 0b1111:
				if (drawTriLow(baseX + 0, baseY)) setBits |= 1;
				if (drawTriLow(baseX + 1, baseY)) setBits |= 2;
				if (drawTriLow(baseX + 2, baseY)) setBits |= 4;
				if (drawTriLow(baseX + 3, baseY)) setBits |= 8;
				break;
			}

			switch (bits >> 4) {
			case 0b0000:
				break;

			case 0b0001:
				if (drawTriLow(baseX + 4, baseY)) setBits |= 16;
				break;

			case 0b0010:
				if (drawTriLow(baseX + 5, baseY)) setBits |= 32;
				break;

			case 0b0011:
				if (drawTriLow(baseX + 4, baseY)) setBits |= 16;
				if (drawTriLow(baseX + 5, baseY)) setBits |= 32;
				break;

			case 0b0100:
				if (drawTriLow(baseX + 6, baseY)) setBits |= 64;
				break;

			case 0b0101:
				if (drawTriLow(baseX + 4, baseY)) setBits |= 16;
				if (drawTriLow(baseX + 6, baseY)) setBits |= 64;
				break;

			case 0b0110:
				if (drawTriLow(baseX + 5, baseY)) setBits |= 32;
				if (drawTriLow(baseX + 6, baseY)) setBits |= 64;
				break;

			case 0b0111:
				if (drawTriLow(baseX + 4, baseY)) setBits |= 16;
				if (drawTriLow(baseX + 5, baseY)) setBits |= 32;
				if (drawTriLow(baseX + 6, baseY)) setBits |= 64;
				break;

			case 0b1000:
				if (drawTriLow(baseX + 7, baseY)) setBits |= 128;
				break;

			case 0b1001:
				if (drawTriLow(baseX + 4, baseY)) setBits |= 16;
				if (drawTriLow(baseX + 7, baseY)) setBits |= 128;
				break;

			case 0b1010:
				if (drawTriLow(baseX + 5, baseY)) setBits |= 32;
				if (drawTriLow(baseX + 7, baseY)) setBits |= 128;
				break;

			case 0b1011:
				if (drawTriLow(baseX + 4, baseY)) setBits |= 16;
				if (drawTriLow(baseX + 5, baseY)) setBits |= 32;
				if (drawTriLow(baseX + 7, baseY)) setBits |= 128;
				break;

			case 0b1100:
				if (drawTriLow(baseX + 6, baseY)) setBits |= 64;
				if (drawTriLow(baseX + 7, baseY)) setBits |= 128;
				break;

			case 0b1101:
				if (drawTriLow(baseX + 4, baseY)) setBits |= 16;
				if (drawTriLow(baseX + 6, baseY)) setBits |= 64;
				if (drawTriLow(baseX + 7, baseY)) setBits |= 128;
				break;

			case 0b1110:
				if (drawTriLow(baseX + 5, baseY)) setBits |= 32;
				if (drawTriLow(baseX + 6, baseY)) setBits |= 64;
				if (drawTriLow(baseX + 7, baseY)) setBits |= 128;
				break;

			case 0b1111:
				if (drawTriLow(baseX + 4, baseY)) setBits |= 16;
				if (drawTriLow(baseX + 5, baseY)) setBits |= 32;
				if (drawTriLow(baseX + 6, baseY)) setBits |= 64;
				if (drawTriLow(baseX + 7, baseY)) setBits |= 128;
				break;
			}

			if (setBits != 0) {
				word |= ((long) setBits) << (y << 3);
			}

			++baseY;
			coverage >>= 8;
		}

		midBins[index] = word;

		return word == -1L;
	}

	private boolean isTriMidCovered(int minX, int minY) {
		final int dx = minX - minPixelX;
		final int dy = minY - minPixelY;
		final int w0_row = wOrigin0 + dx * a0 + dy * b0;
		final int w1_row = wOrigin1 + dx * a1 + dy * b1;
		final int w2_row = wOrigin2 + dx * a2 + dy * b2;

		if ((w0_row | w1_row | w2_row
				| (w0_row + aMid0) | (w1_row + aMid1) | (w2_row + aMid2)
				| (w0_row + bMid0) | (w1_row + bMid1) | (w2_row + bMid2)
				| (w0_row + abMid0) | (w1_row + abMid1) | (w2_row + abMid2)) >= 0) {
			return true;
		} else {
			return false;
		}
	}

	private boolean drawTriLow(int lowX, int lowY) {
		final int index = lowIndex(lowX, lowY);

		final int binOriginX = lowX << LOW_AXIS_SHIFT;
		final int binOriginY = lowY << LOW_AXIS_SHIFT;
		final int minPixelX = this.minPixelX;
		final int minPixelY = this.minPixelY;

		final int minX = minPixelX < binOriginX ? binOriginX : minPixelX;
		final int minY = minPixelY < binOriginY ? binOriginY : minPixelY;
		final int maxX = Math.min(maxPixelX, binOriginX + LOW_BIN_PIXEL_DIAMETER - 1);
		final int maxY = Math.min(maxPixelY, binOriginY + LOW_BIN_PIXEL_DIAMETER - 1);

		long coverage = coverageMask(minX & 7, minY & 7, maxX & 7, maxY & 7);

		// if filling whole bin then do it quick
		if ((coverage & CORNER_COVERAGE_MASK) == CORNER_COVERAGE_MASK && isTriLowCovered(minX,  minY)) {
			lowBins[index] = -1;
			return true;
		}

		long word = lowBins[index];
		coverage &= ~word;

		if (coverage == 0) {
			return false;
		}

		final int a0 = this.a0;
		final int b0 = this.b0;
		final int a1 = this.a1;
		final int b1 = this.b1;
		final int a2 = this.a2;
		final int b2 = this.b2;
		final int x0 = minX & LOW_BIN_PIXEL_INDEX_MASK;
		final int x1 = maxX & LOW_BIN_PIXEL_INDEX_MASK;
		final int y0 = minY & LOW_BIN_PIXEL_INDEX_MASK;
		final int y1 = maxY & LOW_BIN_PIXEL_INDEX_MASK;
		final int dx = minX - minPixelX;
		final int dy = minY - minPixelY;

		int w0_row = wOrigin0 + dx * a0 + dy * b0;
		int w1_row = wOrigin1 + dx * a1 + dy * b1;
		int w2_row = wOrigin2 + dx * a2 + dy * b2;

		for (int y = y0; y <= y1; y++) {
			int w0 = w0_row;
			int w1 = w1_row;
			int w2 = w2_row;
			long mask = 1L << ((y << BIN_AXIS_SHIFT) | x0);

			for (int x = x0; x <= x1; x++) {

				// If p is on or inside all edges, render pixel.
				if ((word & mask) == 0 && (w0 | w1 | w2) >= 0) {
					word |= mask;
				}

				// One step to the right
				w0 += a0;
				w1 += a1;
				w2 += a2;
				mask <<= 1;
			}

			// One row step
			w0_row += b0;
			w1_row += b1;
			w2_row += b2;
		}

		lowBins[index] = word;
		return word == -1L;
	}

	private boolean isTriLowCovered(int minX, int minY) {
		final int dx = minX - minPixelX;
		final int dy = minY - minPixelY;
		final int w0_row = wOrigin0 + dx * a0 + dy * b0;
		final int w1_row = wOrigin1 + dx * a1 + dy * b1;
		final int w2_row = wOrigin2 + dx * a2 + dy * b2;

		if ((w0_row | w1_row | w2_row
				| (w0_row + aLow0) | (w1_row + aLow1) | (w2_row + aLow2)
				| (w0_row + bLow0) | (w1_row + bLow1) | (w2_row + bLow2)
				| (w0_row + abLow0) | (w1_row + abLow1) | (w2_row + abLow2)) >= 0) {
			return true;
		} else {
			return false;
		}
	}


	@Override
	protected boolean testTri(ProjectionVector4f v0, ProjectionVector4f v1, ProjectionVector4f v2) {
		if (!prepareTriBounds(v0, v1, v2)) {
			return false;
		}

		if (triNeedsClipped()) {
			return testClippedLowX(v0, v1, v2);
		}

		prepareTriScan();

		final int bx0 = minPixelX >> TOP_AXIS_SHIFT;
				final int bx1 = maxPixelX >> TOP_AXIS_SHIFT;
			final int by0 = minPixelY >> TOP_AXIS_SHIFT;
			final int by1 = maxPixelY >> TOP_AXIS_SHIFT;

		if (bx0 == bx1 && by0 == by1) {
			return testTriTop(bx0, by0);
		} else {
			for (int by = by0; by <= by1; by++) {
				for (int bx = bx0; bx <= bx1; bx++) {
					if (testTriTop(bx, by)) {
						return true;
					}
				}
			}

			return false;
		}
	}

	private boolean testTriTop(final int topX, final int topY) {
		final int index = topIndex(topX, topY);
		final long word = topBins[index];

		if (word == -1L) {
			// bin fully occluded
			return false;
		}

		final int binOriginX = topX << TOP_AXIS_SHIFT;
		final int binOriginY = topY << TOP_AXIS_SHIFT;
		final int minX = Math.max(minPixelX, binOriginX);
		final int maxX = Math.min(maxPixelX, binOriginX + TOP_BIN_PIXEL_DIAMETER - 1);
		final int minY = Math.max(minPixelY, binOriginY);
		final int maxY = Math.min(maxPixelY, binOriginY + TOP_BIN_PIXEL_DIAMETER - 1);

		final int midX0 = minX >>> MID_AXIS_SHIFT;
		final int midX1 = maxX >>> MID_AXIS_SHIFT;
				final int midY0 = minY >>> MID_AXIS_SHIFT;
		final int midY1 = maxY >>> MID_AXIS_SHIFT;

				// handle single bin case
				if (midX0 == midX1 && midY0 == midY1) {
					return isPixelClear(word, midX0, midY0) && testTriMid(midX0, midY0);
				}

				// if checking whole bin then do it quick
				if (((minX | minY) & TOP_BIN_PIXEL_INDEX_MASK)== 0 && (maxX & maxY & TOP_BIN_PIXEL_INDEX_MASK) == TOP_BIN_PIXEL_INDEX_MASK) {
					final int a0 = this.a0;
					final int b0 = this.b0;
					final int a1 = this.a1;
					final int b1 = this.b1;
					final int a2 = this.a2;
					final int b2 = this.b2;
					final int dx = binOriginX - minPixelX;
					final int dy = binOriginY - minPixelY;
					final int w0_row = wOrigin0 + dx * a0 + dy * b0;
					final int w1_row = wOrigin1 + dx * a1 + dy * b1;
					final int w2_row = wOrigin2 + dx * a2 + dy * b2;

					if ((w0_row | w1_row | w2_row
							| (w0_row + aTop0) | (w1_row + aTop1) | (w2_row + aTop2)
							| (w0_row + bTop0) | (w1_row + bTop1) | (w2_row + bTop2)
							| (w0_row + abTop0) | (w1_row + abTop1) | (w2_row + abTop2)) >= 0) {
						// word already known to be not fully occluded at this point
						return true;
					}
				}

				for (int midY = midY0; midY <= midY1; midY++) {
					for (int midX = midX0; midX <= midX1; midX++) {
						if (isPixelClear(word, midX, midY) && testTriMid(midX, midY)) {
							return true;
						}
					}
				}

				return false;
	}

	/**
	 * Returns true when bin fully occluded
	 */
	private boolean testTriMid(final int midX, final int midY) {
		final int index = midIndex(midX, midY);
		final long word = midBins[index];

		final int binOriginX = midX << MID_AXIS_SHIFT;
		final int binOriginY = midY << MID_AXIS_SHIFT;
		final int minX = Math.max(minPixelX, binOriginX);
		final int maxX = Math.min(maxPixelX, binOriginX + MID_BIN_PIXEL_DIAMETER - 1);
		final int minY = Math.max(minPixelY, binOriginY);
		final int maxY = Math.min(maxPixelY, binOriginY + MID_BIN_PIXEL_DIAMETER - 1);

		final int lowX0 = minX >>> LOW_AXIS_SHIFT;
		final int lowX1 = maxX >>> LOW_AXIS_SHIFT;
					final int lowY0 = minY >>> LOW_AXIS_SHIFT;
				final int lowY1 = maxY >>> LOW_AXIS_SHIFT;

					// handle single bin case
					if (lowX0 == lowX1 && lowY0 == lowY1) {
						return isPixelClear(word, lowX0, lowY0) && testTriLow(lowX0, lowY0);
					}

					// if checking whole bin then do it quick
					if (((minX | minY) & MID_BIN_PIXEL_INDEX_MASK)== 0 && (maxX & maxY & MID_BIN_PIXEL_INDEX_MASK) == MID_BIN_PIXEL_INDEX_MASK) {
						final int a0 = this.a0;
						final int b0 = this.b0;
						final int a1 = this.a1;
						final int b1 = this.b1;
						final int a2 = this.a2;
						final int b2 = this.b2;
						final int dx = binOriginX - minPixelX;
						final int dy = binOriginY - minPixelY;
						final int w0_row = wOrigin0 + dx * a0 + dy * b0;
						final int w1_row = wOrigin1 + dx * a1 + dy * b1;
						final int w2_row = wOrigin2 + dx * a2 + dy * b2;

						if ((w0_row | w1_row | w2_row
								| (w0_row + aMid0) | (w1_row + aMid1) | (w2_row + aMid2)
								| (w0_row + bMid0) | (w1_row + bMid1) | (w2_row + bMid2)
								| (w0_row + abMid0) | (w1_row + abMid1) | (w2_row + abMid2)) >= 0) {
							// word already known to be not fully occluded at this point
							return true;
						}
					}

					for (int lowY = lowY0; lowY <= lowY1; lowY++) {
						for (int lowX = lowX0; lowX <= lowX1; lowX++) {
							if (isPixelClear(word, lowX, lowY) && testTriLow(lowX, lowY)) {
								return true;
							}
						}
					}

					return false;
	}

	private boolean testTriLow(int lowX, int lowY) {
		final int index = lowIndex(lowX, lowY);
		final long word = lowBins[index];

		final int binOriginX = lowX << LOW_AXIS_SHIFT;
		final int binOriginY = lowY << LOW_AXIS_SHIFT;
		final int minX = Math.max(minPixelX, binOriginX);
		final int maxX = Math.min(maxPixelX, binOriginX + LOW_BIN_PIXEL_DIAMETER - 1);
		final int minY = Math.max(minPixelY, binOriginY);
		final int maxY = Math.min(maxPixelY, binOriginY + LOW_BIN_PIXEL_DIAMETER - 1);
		final int x0 = minX & LOW_BIN_PIXEL_INDEX_MASK;
		final int x1 = maxX & LOW_BIN_PIXEL_INDEX_MASK;
		final int y0 = minY & LOW_BIN_PIXEL_INDEX_MASK;
		final int y1 = maxY & LOW_BIN_PIXEL_INDEX_MASK;
		final int a0 = this.a0;
		final int b0 = this.b0;
		final int a1 = this.a1;
		final int b1 = this.b1;
		final int a2 = this.a2;
		final int b2 = this.b2;
		final int dx = minX - minPixelX;
		final int dy = minY - minPixelY;

		int w0_row = wOrigin0 + dx * a0 + dy * b0;
		int w1_row = wOrigin1 + dx * a1 + dy * b1;
		int w2_row = wOrigin2 + dx * a2 + dy * b2;

		// handle single pixel case
		if (x0 == x1 && y0 == y1) {
			if (testPixelInWordPreMasked(word, x0, y0)  && (w0_row | w1_row | w2_row) >= 0)  {
				return (word & pixelMask(x0, y0)) == 0;
			}
		}

		// if checking whole bin then do it quick
		if ((x0 | y0)== 0 && (x1 & y1) == LOW_BIN_PIXEL_INDEX_MASK) {
			if ((w0_row | w1_row | w2_row
					| (w0_row + aLow0) | (w1_row + aLow1) | (w2_row + aLow2)
					| (w0_row + bLow0) | (w1_row + bLow1) | (w2_row + bLow2)
					| (w0_row + abLow0) | (w1_row + abLow1) | (w2_row + abLow2)) >= 0) {

				// word already known to be not fully occluded at this point
				return true;
			}
		}

		for (int y = y0; y <= y1; y++) {
			int w0 = w0_row;
			int w1 = w1_row;
			int w2 = w2_row;

			for (int x = x0; x <= x1; x++) {
				// If p is on or inside all edges, check pixel.
				if ((w0 | w1 | w2) >= 0 && (word & (1L << ((y << BIN_AXIS_SHIFT) | x))) == 0) {
					return true;
				}

				// One step to the right
				w0 += a0;
				w1 += a1;
				w2 += a2;
			}

			// One row step
			w0_row += b0;
			w1_row += b1;
			w2_row += b2;
		}

		return false;
	}
}
