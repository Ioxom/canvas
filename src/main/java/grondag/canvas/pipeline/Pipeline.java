/*
 *  Copyright 2019, 2020 grondag
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not
 *  use this file except in compliance with the License.  You may obtain a copy
 *  of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 *  License for the specific language governing permissions and limitations under
 *  the License.
 */

package grondag.canvas.pipeline;

import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import net.minecraft.util.Identifier;

import grondag.canvas.pipeline.PipelineConfig.DebugConfig;
import grondag.canvas.pipeline.PipelineConfig.ImageConfig;

public class Pipeline {
	private static boolean reload = true;
	private static int lastWidth;
	private static int lastHeight;

	private static final Object2ObjectOpenHashMap<Identifier, Image> IMAGES = new Object2ObjectOpenHashMap<>();

	static Image getImage(Identifier id) {
		return IMAGES.get(id);
	}

	// WIP: remove
	static boolean needsReload() {
		return reload;
	}

	public static void reload() {
		reload = true;
	}

	public static void close() {
		closeInner();
		reload = true;
	}

	private static void closeInner() {
		if (!IMAGES.isEmpty()) {
			IMAGES.values().forEach(img -> img.close());
			IMAGES.clear();
		}

		BufferDebug.clear();
	}

	static void activate(int width, int height) {
		assert RenderSystem.isOnRenderThread();

		if (reload || lastWidth != width || lastHeight != height) {
			reload = false;
			lastWidth = width;
			lastHeight = height;
			closeInner();
			activateInner(width, height);
		}
	}

	private static void activateInner(int width, int height) {
		final PipelineConfig config = new PipelineConfig();

		for (final ImageConfig img : config.images) {
			IMAGES.put(img.id, new Image(img.id, width, height, img.hdr, img.blur, img.lod));
		}

		for (final DebugConfig debug : config.debugs) {
			BufferDebug.add(debug.mainImage, debug.sneakImage, debug.lod, debug.label);
		}
	}
}
