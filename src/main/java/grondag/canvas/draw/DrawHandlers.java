package grondag.canvas.draw;


import net.minecraft.client.render.RenderLayer;

import grondag.canvas.apiimpl.RenderMaterialImpl.Value;
import grondag.canvas.material.MaterialContext;
import grondag.canvas.material.MaterialVertexFormat;

public class DrawHandlers {
	private static int nextHandlerIndex = 0;

	public final int index = nextHandlerIndex++;

	public static DrawHandler get(MaterialContext context, MaterialVertexFormat format, Value mat) {
		return VanillaDrawHandler.INSTANCE;
	}

	public static DrawHandler get(MaterialContext context, RenderLayer layer) {
		return VanillaDrawHandler.INSTANCE;
	}
}