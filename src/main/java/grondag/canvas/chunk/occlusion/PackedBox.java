package grondag.canvas.chunk.occlusion;

public abstract class PackedBox {
	private PackedBox() { }

	private static final int SHIFT_X0 = 0;
	private static final int SHIFT_Y0 = SHIFT_X0 + 5;
	private static final int SHIFT_Z0 = SHIFT_Y0 + 5;
	private static final int SHIFT_X1 = SHIFT_Z0 + 5;
	private static final int SHIFT_Y1 = SHIFT_X1 + 5;
	private static final int SHIFT_Z1 = SHIFT_Y1 + 5;
	private static final int SHIFT_PLANE = SHIFT_Z1 + 5;

	public static final int PLANE_NONE = 0;
	public static final int PLANE_X = 1;
	public static final int PLANE_Y = 2;
	public static final int PLANE_Z = 3;

	public static int pack(int x0, int y0, int z0, int x1, int y1, int z1) {
		return x0 | (y0 << SHIFT_Y0) | (z0 << SHIFT_Z0)
				| ((x1) << SHIFT_X1) | ((y1) << SHIFT_Y1) | ((z1) << SHIFT_Z1)
				| (computePlane(x0, y0, z0, x1, y1, z1) << SHIFT_PLANE);
	}

	private static int computePlane(int x0, int y0, int z0, int x1, int y1, int z1) {
		return x1 == x0 ? PLANE_X : y1 == y0 ? PLANE_Y : z1 == z0 ? PLANE_Z : PLANE_NONE;
	}

	public static int plane(int packed) {
		return (packed >>> SHIFT_PLANE) & 3;
	}

	public static int x0(int packed) {
		return (packed & 31);
	}

	public static int y0(int packed) {
		return (packed >>> SHIFT_Y0) & 31;
	}

	public static int z0(int packed) {
		return (packed >>> SHIFT_Z0) & 31;
	}

	public static int x1(int packed) {
		return ((packed >>> SHIFT_X1) & 31);
	}

	public static int y1(int packed) {
		return ((packed >>> SHIFT_Y1) & 31);
	}

	public static int z1(int packed) {
		return ((packed >>> SHIFT_Z1) & 31);
	}

	public static String toString(int packed) {
		return "(" + x0(packed) + ", " + y0(packed) + ", " + z0(packed) + "), ("
				+ x1(packed) + ", " + y1(packed) + ", " + z1(packed) + ")";
	}

	public static final int FULL_BOX = pack(0, 0, 0, 16, 16, 16);

	public static final int EMPTY_BOX = 0;
}
