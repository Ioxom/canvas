package grondag.canvas.terrain.occlusion.region.area;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

import grondag.canvas.terrain.occlusion.region.OcclusionBitPrinter;

public class Area {
	private static final int[] AREA_KEY_TO_INDEX = new int[0x10000];
	private static final int[] AREA_INDEX_TO_KEY;

	public static final int AREA_COUNT;

	private static final int[] SECTION_KEYS;
	private static final int[] SECTION_INDEX;

	public static final int SECTION_COUNT;

	private static final long[] AREA_BITS;

	public static int keyToIndex(int key) {
		return AREA_KEY_TO_INDEX[key];
	}

	public static int indexToKey(int index) {
		return AREA_INDEX_TO_KEY[index];
	}

	public static int sectionToAreaIndex(int sectionIndex) {
		return SECTION_INDEX[sectionIndex];
	}

	static {
		final IntOpenHashSet areas = new IntOpenHashSet();

		areas.add(Area.areaKey(0, 0, 15, 15));

		areas.add(Area.areaKey(1, 0, 15, 15));
		areas.add(Area.areaKey(0, 0, 14, 15));
		areas.add(Area.areaKey(0, 1, 15, 15));
		areas.add(Area.areaKey(0, 0, 15, 14));

		for (int x0 = 0; x0 <= 15; x0++) {
			for (int x1 = x0; x1 <= 15; x1++) {
				for (int y0 = 0; y0 <= 15; y0++) {
					for(int y1 = y0; y1 <= 15; y1++) {
						areas.add(Area.areaKey(x0, y0, x1, y1));
					}
				}
			}
		}

		AREA_COUNT = areas.size();
		AREA_INDEX_TO_KEY = new int[AREA_COUNT];
		AREA_BITS = new long[AREA_COUNT * 4];

		int i = 0;

		for(final int k : areas) {
			AREA_INDEX_TO_KEY[i++] = k;
		}

		IntArrays.quickSort(AREA_INDEX_TO_KEY, (a, b) -> {
			final int result = Integer.compare(Area.size(b), Area.size(a));

			// within same area size, prefer more compact rectangles
			return result == 0 ? Integer.compare(Area.edgeCount(a), Area.edgeCount(b)) : result;
		});

		for (int j = 0; j < AREA_COUNT; j++) {
			AREA_KEY_TO_INDEX[AREA_INDEX_TO_KEY[j]] = j;
		}

		final IntArrayList sections = new IntArrayList();

		for (int j = 0; j < AREA_COUNT; ++j) {
			final int a = AREA_INDEX_TO_KEY[j];

			if ((Area.x0(a) == 0  &&  Area.x1(a) == 15) || (Area.y0(a) == 0  &&  Area.y1(a) == 15)) {
				sections.add(indexToKey(j));
			}
		}

		SECTION_COUNT = sections.size();
		SECTION_KEYS = sections.toArray(new int[SECTION_COUNT]);
		SECTION_INDEX = new int[SECTION_COUNT];

		for (int k = 0; k < SECTION_COUNT; ++k) {
			SECTION_INDEX[k] = keyToIndex(SECTION_KEYS[k]);
		}

		final long[] xMasks = new long[256];
		final int[] yBits = new int[256];
		final long[] yMasks = new long[16];

		for (int x0 = 0; x0 <= 15; ++x0) {
			for (int x1 = x0; x1 <= 15; ++x1) {
				final long template  = (0xFFFF << x0) & (0xFFFF >> (15 - x1));
				xMasks[x0 | (x1 << 4)] = template | (template << 16) | (template << 32) | (template << 48);
			}
		}

		for (int y0 = 0; y0 <= 15; ++y0) {
			for (int y1 = y0; y1 <= 15; ++y1) {
				yBits[y0 | (y1 << 4)] = (0xFFFF << y0) & (0xFFFF >> (15 - y1));
			}
		}

		yMasks[0b0000] = 0L;
		yMasks[0b0001] = 0x000000000000FFFFL;
		yMasks[0b0010] = 0x00000000FFFF0000L;
		yMasks[0b0100] = 0x0000FFFF00000000L;
		yMasks[0b1000] = 0xFFFF000000000000L;
		yMasks[0b0011] = 0x00000000FFFFFFFFL;
		yMasks[0b0110] = 0x0000FFFFFFFF0000L;
		yMasks[0b1100] = 0xFFFFFFFF00000000L;
		yMasks[0b0111] = 0x0000FFFFFFFFFFFFL;
		yMasks[0b1110] = 0xFFFFFFFFFFFF0000L;
		yMasks[0b1111] = 0xFFFFFFFFFFFFFFFFL;

		int j = 0;

		for (int areaIndex = 0; areaIndex < AREA_COUNT; ++areaIndex) {
			final int areaKey = indexToKey(areaIndex);
			AREA_BITS[j++] = yMasks[(yBits[areaKey >> 8] >> (0 << 2)) & 0xF] & xMasks[areaKey & 0xFF];
			AREA_BITS[j++] = yMasks[(yBits[areaKey >> 8] >> (1 << 2)) & 0xF] & xMasks[areaKey & 0xFF];
			AREA_BITS[j++] = yMasks[(yBits[areaKey >> 8] >> (2 << 2)) & 0xF] & xMasks[areaKey & 0xFF];
			AREA_BITS[j++] = yMasks[(yBits[areaKey >> 8] >> (3 << 2)) & 0xF] & xMasks[areaKey & 0xFF];
		}
	}

	public static boolean isIncludedBySample(long[] sample, int sampleStart, int areaIndex) {
		final long template = bitsFromIndex(areaIndex, 0);
		final long template1 = bitsFromIndex(areaIndex, 1);
		final long template2 = bitsFromIndex(areaIndex, 2);
		final long template3 = bitsFromIndex(areaIndex, 3);

		return (template & sample[sampleStart]) == template
				&& (template1 & sample[sampleStart + 1]) == template1
				&& (template2 & sample[sampleStart + 2]) == template2
				&& (template3 & sample[sampleStart + 3]) == template3;
	}

	public static boolean intersectsWithSample(long[] sample, int sampleStart, int areaKey) {
		return (bitsFromKey(areaKey, 0) & sample[sampleStart]) != 0
				|| (bitsFromKey(areaKey, 1) & sample[++sampleStart]) != 0
				|| (bitsFromKey(areaKey, 2) & sample[++sampleStart]) != 0
				|| (bitsFromKey(areaKey, 3) & sample[++sampleStart]) != 0;
	}

	public static boolean isAdditive(long[] sample, int sampleStart, int areaKey) {
		return (bitsFromKey(areaKey, 0) | sample[sampleStart]) != sample[sampleStart]
				|| (bitsFromKey(areaKey, 1) | sample[++sampleStart]) != sample[sampleStart]
						|| (bitsFromKey(areaKey, 2) | sample[++sampleStart]) != sample[sampleStart]
								|| (bitsFromKey(areaKey, 3) | sample[++sampleStart]) != sample[sampleStart];
	}

	public static void clearBits(long[] targetBits, int startIndex, int areaIndex) {
		areaIndex <<= 2;
		targetBits[startIndex] &= ~AREA_BITS[areaIndex++];
		targetBits[++startIndex] &= ~AREA_BITS[areaIndex++];
		targetBits[++startIndex] &= ~AREA_BITS[areaIndex++];
		targetBits[++startIndex] &= ~AREA_BITS[areaIndex++];
	}

	static long bitsFromKey(int areaKey, int y) {
		return AREA_BITS[(keyToIndex(areaKey) << 2) + y];
	}

	static long bitsFromIndex(int areaIndex, int y) {
		return AREA_BITS[(areaIndex << 2) + y];
	}

	public static void printShape(int areaKey) {
		final long[] bits = new long[4];
		bits[0] = bitsFromKey(areaKey, 0);
		bits[1] = bitsFromKey(areaKey, 1);
		bits[2] = bitsFromKey(areaKey, 2);
		bits[3] = bitsFromKey(areaKey, 3);

		OcclusionBitPrinter.printShape(bits, 0);
	}

	public static int areaKey(int x0, int y0, int x1, int y1) {
		return x0 | (x1 << 4) | (y0 << 8) | (y1 << 12);
	}

	public static int x0(int areaKey) {
		return areaKey & 15;
	}

	public static int y0(int areaKey) {
		return (areaKey >> 8) & 15;
	}

	public static int x1(int areaKey) {
		return (areaKey >> 4) & 15;
	}

	public static int y1(int areaKey) {
		return (areaKey >> 12) & 15;
	}

	public static int size(int areaKey) {
		final int x0 = x0(areaKey);
		final int y0 = y0(areaKey);
		final int x1 = x1(areaKey);
		final int y1 = y1(areaKey);

		return (x1 - x0 + 1) * (y1 - y0 + 1);
	}

	public static int edgeCount(int areaKey) {
		final int x0 = x0(areaKey);
		final int y0 = y0(areaKey);
		final int x1 = x1(areaKey);
		final int y1 = y1(areaKey);

		final int x = x1 - x0 + 1;
		final int y = y1 - y0 + 1;
		return x + y;
	}

	public static void printArea(int areaKey) {
		final int x0 = x0(areaKey);
		final int y0 = y0(areaKey);
		final int x1 = x1(areaKey);
		final int y1 = y1(areaKey);

		final int x = x1 - x0 + 1;
		final int y = y1 - y0 + 1;
		final int a = x * y;
		System.out.println(String.format("%d x %d, area %d, (%d, %d) to (%d, %d)", x, y, a, x0, y0, x1, y1));
	}
}