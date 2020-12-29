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

package grondag.canvas.pipeline.config.util;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import grondag.canvas.CanvasMod;

public abstract class NamedConfig<T extends NamedConfig<T>> extends AbstractConfig {
	public final String name;
	public final boolean isDuplicateName;

	@SuppressWarnings("unchecked")
	protected NamedConfig(ConfigContext ctx, String name) {
		super(ctx);
		this.name = name;

		if (nameMap().putIfAbsent(name, (T) this) != null) {
			CanvasMod.LOG.warn(String.format("Invalid pipeline config. Encountered duplicate name %s for %s", name, this.getClass().getSimpleName()));
			isDuplicateName = true;
		} else {
			isDuplicateName = false;
		}
	}

	public abstract Object2ObjectOpenHashMap<String, T> nameMap();
}
