/*******************************************************************************
 * Copyright 2019 grondag
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy
 * of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 ******************************************************************************/

package grondag.canvas.material;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import org.lwjgl.opengl.GL11;

import com.google.common.io.CharStreams;
import com.mojang.blaze3d.platform.GLX;

import grondag.canvas.Canvas;
import grondag.canvas.varia.CanvasGlHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

abstract class AbstractGlShader {
    public final Identifier shaderSource;

    private final int shaderType;
    public final int spriteDepth;
    public final ShaderContext context;

    private int glId = -1;
    private boolean needsLoad = true;
    private boolean isErrored = false;

    AbstractGlShader(Identifier shaderSource, int shaderType, int spriteDepth, ShaderContext context) {
        this.shaderSource = shaderSource;
        this.shaderType = shaderType;
        this.spriteDepth = spriteDepth;
        this.context = context;
    }

    /**
     * Call after render / resource refresh to force shader reload.
     */
    public final void forceReload() {
        this.needsLoad = true;
    }

    public final int glId() {
        if (this.needsLoad)
            this.load();

        return this.isErrored ? -1 : this.glId;
    }

    private final void load() {
        this.needsLoad = false;
        this.isErrored = false;
        try {
            if (this.glId <= 0) {
                this.glId = GLX.glCreateShader(shaderType);
                if (this.glId == 0) {
                    this.glId = -1;
                    this.isErrored = true;
                    return;
                }
            }

            GLX.glShaderSource(this.glId, this.getSource());
            GLX.glCompileShader(this.glId);

            if (GLX.glGetShaderi(this.glId, GLX.GL_COMPILE_STATUS) == GL11.GL_FALSE)
                throw new RuntimeException(CanvasGlHelper.getShaderInfoLog(this.glId));

        } catch (Exception e) {
            this.isErrored = true;
            if (this.glId > 0) {
                GLX.glDeleteShader(glId);
                this.glId = -1;
            }
            Canvas.INSTANCE.log().error(I18n.translate("misc.fail_create_shader", this.shaderSource.toString(),
                    Integer.toString(this.spriteDepth), e.getMessage()));
        }
    }

    public String buildSource(String librarySource) {
        String result = getShaderSource(this.shaderSource);
        result = result.replaceAll("#version\\s+120", "");
        result = librarySource + result;

        if (spriteDepth > 1)
            result = result.replaceAll("#define LAYER_COUNT 1", String.format("#define LAYER_COUNT %d", spriteDepth));

        
        result = result.replaceAll("#define CONTEXT 0", "#define CONTEXT " + context.ordinal());

        return result;
    }

    abstract String getSource();

    public static String getShaderSource(Identifier shaderSource) {
        try {
            ResourceManager rm = MinecraftClient.getInstance().getResourceManager();
            InputStream in = rm.getResource(shaderSource).getInputStream();
            if (in == null)
                return "";
            final Reader reader = new InputStreamReader(in);
            return CharStreams.toString(reader);
        } catch (IOException e) {
            return "";
        }
    }
}