package pu.web.client;

import pu.web.client.resources.shaders.Shaders;

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.ImageElement;
import com.google.gwt.resources.client.ImageResource;
import com.googlecode.gwtgl.array.Float32Array;
import com.googlecode.gwtgl.binding.WebGLBuffer;
import com.googlecode.gwtgl.binding.WebGLProgram;
import com.googlecode.gwtgl.binding.WebGLRenderingContext;
import com.googlecode.gwtgl.binding.WebGLShader;
import com.googlecode.gwtgl.binding.WebGLTexture;

public class PU_Engine
{
	public static final int SCREEN_WIDTH = 964;
	public static final int SCREEN_HEIGHT = 720;

	public static final int BLENDMODE_NONE = 0;
	public static final int BLENDMODE_BLEND = 1;
	public static final int BLENDMODE_ADD = 2;
	public static final int BLENDMODE_MOD = 3;
	
	private static final int SPRITEBATCH_MAX_DATASIZE = 40000;

	private int mBlendMode = BLENDMODE_NONE;
	private PU_Shader mShaderSolid;
	private PU_Shader mShaderTex;
	private PU_Shader mCurrentShader;
	private boolean mUseTexCoords = false;
	private float mColor[] = new float[] { 0.0f, 0.0f, 0.0f, 1.0f };
	private WebGLTexture mLastBoundTexture = null;
	
	private float[] mSpriteBatchData = new float[SPRITEBATCH_MAX_DATASIZE];
	private int mSpriteBatchDrawCount = 0;
	
	private WebGLRenderingContext mGlContext;

	public PU_Engine(WebGLRenderingContext glContext)
	{
		// Keep a private reference to this even though it's static, for that
		// little performance gain
		this.mGlContext = glContext;
	}

	public void init()
	{
		initShaders();
		useTextureShader();
		
		mGlContext.enableVertexAttribArray(mCurrentShader.getAPosition());
		mGlContext.disableVertexAttribArray(mCurrentShader.getATexCoord());

		mGlContext.viewport(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT);
	}
	
	public void clear()
	{
		mGlContext.clearColor(0.0f, 0.0f, 0.0f, 255.0f);
		mGlContext.clear(WebGLRenderingContext.COLOR_BUFFER_BIT);
	}

	public void initShaders()
	{
		WebGLShader fragmentShaderSolid = getShader(WebGLRenderingContext.FRAGMENT_SHADER, Shaders.INSTANCE.fragmentShaderSolid().getText());
		WebGLShader fragmentShaderTex = getShader(WebGLRenderingContext.FRAGMENT_SHADER, Shaders.INSTANCE.fragmentShaderTex().getText());
		WebGLShader vertexShader = getShader(WebGLRenderingContext.VERTEX_SHADER, Shaders.INSTANCE.vertexShader().getText());

		WebGLProgram program = mGlContext.createProgram();
		mGlContext.attachShader(program, vertexShader);
		mGlContext.attachShader(program, fragmentShaderSolid);
		mGlContext.linkProgram(program);
		if (!mGlContext.getProgramParameterb(program, WebGLRenderingContext.LINK_STATUS))
		{
			throw new RuntimeException("Could not initialise solid shader");
		}
		mShaderSolid = new PU_Shader(program);

		program = mGlContext.createProgram();
		mGlContext.attachShader(program, vertexShader);
		mGlContext.attachShader(program, fragmentShaderTex);
		mGlContext.linkProgram(program);

		if (!mGlContext.getProgramParameterb(program, WebGLRenderingContext.LINK_STATUS))
		{
			throw new RuntimeException("Could not initialise texture shader");
		}
		mShaderTex = new PU_Shader(program);
	}

	public void useSolidShader()
	{
		if (mCurrentShader != mShaderSolid)
		{
			mCurrentShader = mShaderSolid;
			mGlContext.useProgram(mShaderSolid.getProgram());

			setOrthographicProjection();
		}
	}

	public void useTextureShader()
	{
		if (mCurrentShader != mShaderTex)
		{
			mCurrentShader = mShaderTex;
			mGlContext.useProgram(mShaderTex.getProgram());

			setOrthographicProjection();
		}
	}

	private WebGLShader getShader(int type, String source)
	{
		WebGLShader shader = mGlContext.createShader(type);

		mGlContext.shaderSource(shader, source);
		mGlContext.compileShader(shader);

		if (!mGlContext.getShaderParameterb(shader, WebGLRenderingContext.COMPILE_STATUS))
		{
			throw new RuntimeException(mGlContext.getShaderInfoLog(shader));
		}

		return shader;
	}

	public void setOrthographicProjection()
	{
		float projection[] = new float[] { 2.0f / SCREEN_WIDTH, 0.0f, 0.0f, 0.0f, 0.0f, -2.0f / SCREEN_HEIGHT, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, -1.0f, 1.0f, 0.0f, 1.0f };

		mGlContext.uniformMatrix4fv(mCurrentShader.getUProjection(), false, projection);
	}

	public void setBlendMode(int blendMode)
	{
		if (mBlendMode != blendMode)
		{
			mBlendMode = blendMode;
			switch (blendMode)
			{
			case BLENDMODE_NONE:
				mGlContext.disable(WebGLRenderingContext.BLEND);
				break;

			case BLENDMODE_BLEND:
				mGlContext.enable(WebGLRenderingContext.BLEND);
				mGlContext.blendFunc(WebGLRenderingContext.SRC_ALPHA, WebGLRenderingContext.ONE_MINUS_SRC_ALPHA);
				break;

			case BLENDMODE_ADD:
				mGlContext.enable(WebGLRenderingContext.BLEND);
				mGlContext.blendFunc(WebGLRenderingContext.SRC_ALPHA, WebGLRenderingContext.ONE);
				break;

			case BLENDMODE_MOD:
				mGlContext.enable(WebGLRenderingContext.BLEND);
				mGlContext.blendFunc(WebGLRenderingContext.ZERO, WebGLRenderingContext.SRC_COLOR);
				break;
			}
		}
	}

	public void enableTexCoords(boolean enabled)
	{
		if (mUseTexCoords != enabled)
		{
			mUseTexCoords = enabled;
			if (enabled)
			{
				mGlContext.enableVertexAttribArray(mCurrentShader.getATexCoord());
			} else
			{
				mGlContext.disableVertexAttribArray(mCurrentShader.getATexCoord());
			}
		}
	}

	public void setColor(int red, int green, int blue, int alpha)
	{
		mColor[0] = ((float) red / 255.0f);
		mColor[1] = ((float) green / 255.0f);
		mColor[2] = ((float) blue / 255.0f);
		mColor[3] = ((float) alpha / 255.0f);
	}

	public void setPrimitiveDrawingState()
	{
		setBlendMode(mBlendMode);
		enableTexCoords(false);
		useSolidShader();

		mGlContext.uniform4fv(mCurrentShader.getUColor(), mColor);
	}
	
	public void renderLine(int x1, int y1, int x2, int y2)
	{
		setPrimitiveDrawingState();

		float vertices[] = new float[4];
		
		vertices[0] = (float)x1 + 0.5f;
		vertices[1] = (float)y1 + 0.5f;
		
		vertices[2] = (float)x2 + 0.5f;
		vertices[3] = (float)y2 + 0.5f;
		

		WebGLBuffer buffer = mGlContext.createBuffer();
		mGlContext.bindBuffer(WebGLRenderingContext.ARRAY_BUFFER, buffer);
		mGlContext.bufferData(WebGLRenderingContext.ARRAY_BUFFER, Float32Array.create(vertices), WebGLRenderingContext.STREAM_DRAW);

		mGlContext.vertexAttribPointer(mCurrentShader.getAPosition(), 2, WebGLRenderingContext.FLOAT, false, 0, 0);
		mGlContext.drawArrays(WebGLRenderingContext.LINE_STRIP, 0, 2);
	}
	
	public void renderRect(int x, int y, int width, int height)
	{
		setPrimitiveDrawingState();

		float vertices[] = new float[10];
		
		vertices[0] = (float)x + 0.5f;
		vertices[1] = (float)y + 0.5f;
		
		vertices[2] = (float)x+width + 0.5f;
		vertices[3] = (float)y + 0.5f;
		
		vertices[4] = (float)x+width + 0.5f;
		vertices[5] = (float)y+height + 0.5f;
		
		vertices[6] = (float)x + 0.5f;
		vertices[7] = (float)y+height + 0.5f;
		
		vertices[8] = (float)x + 0.5f;
		vertices[9] = (float)y + 0.5f;
		
		WebGLBuffer buffer = mGlContext.createBuffer();
		mGlContext.bindBuffer(WebGLRenderingContext.ARRAY_BUFFER, buffer);
		mGlContext.bufferData(WebGLRenderingContext.ARRAY_BUFFER, Float32Array.create(vertices), WebGLRenderingContext.STREAM_DRAW);

		mGlContext.vertexAttribPointer(mCurrentShader.getAPosition(), 2, WebGLRenderingContext.FLOAT, false, 0, 0);
		mGlContext.drawArrays(WebGLRenderingContext.LINE_STRIP, 0, 5);
	}

	public void renderFillRect(int x, int y, int width, int height)
	{
		setPrimitiveDrawingState();

		float vertices[] = new float[8];

		float xMin = x;
		float xMax = x + width;
		float yMin = y;
		float yMax = y + height;

		vertices[0] = xMin;
		vertices[1] = yMin;
		vertices[2] = xMax;
		vertices[3] = yMin;
		vertices[4] = xMin;
		vertices[5] = yMax;
		vertices[6] = xMax;
		vertices[7] = yMax;

		WebGLBuffer buffer = mGlContext.createBuffer();
		mGlContext.bindBuffer(WebGLRenderingContext.ARRAY_BUFFER, buffer);
		mGlContext.bufferData(WebGLRenderingContext.ARRAY_BUFFER, Float32Array.create(vertices), WebGLRenderingContext.STREAM_DRAW);

		mGlContext.vertexAttribPointer(mCurrentShader.getAPosition(), 2, WebGLRenderingContext.FLOAT, false, 0, 0);
		mGlContext.drawArrays(WebGLRenderingContext.TRIANGLE_STRIP, 0, 4);
	}
		
	WebGLTexture createEmptyTexture()
	{
		WebGLTexture texture = mGlContext.createTexture();
		bindTexture(texture);
		mGlContext.texParameteri(WebGLRenderingContext.TEXTURE_2D, WebGLRenderingContext.TEXTURE_MAG_FILTER, WebGLRenderingContext.LINEAR);
		mGlContext.texParameteri(WebGLRenderingContext.TEXTURE_2D, WebGLRenderingContext.TEXTURE_MIN_FILTER, WebGLRenderingContext.LINEAR);
		mGlContext.texParameteri(WebGLRenderingContext.TEXTURE_2D, WebGLRenderingContext.TEXTURE_WRAP_S, WebGLRenderingContext.CLAMP_TO_EDGE);
		mGlContext.texParameteri(WebGLRenderingContext.TEXTURE_2D, WebGLRenderingContext.TEXTURE_WRAP_T, WebGLRenderingContext.CLAMP_TO_EDGE);
		return texture;
	}
	
	public void fillTexture(WebGLTexture texture, Element element)
	{
		bindTexture(texture);
		mGlContext.texImage2D(WebGLRenderingContext.TEXTURE_2D, 0, WebGLRenderingContext.RGBA, WebGLRenderingContext.RGBA, WebGLRenderingContext.UNSIGNED_BYTE, element.<ImageElement>cast());
	}
	
	public void bindTexture(WebGLTexture texture)
	{
		if(mLastBoundTexture != texture)
		{
			mGlContext.bindTexture(WebGLRenderingContext.TEXTURE_2D, texture);
			mLastBoundTexture = texture;
		}
	}
	
	public void renderTexture(PU_Image image, PU_Rect srcRect, PU_Rect dstRect)
	{
		useTextureShader();
		
		if(mLastBoundTexture != image.getTexture())
		{
			mGlContext.activeTexture(WebGLRenderingContext.TEXTURE0);
			mGlContext.bindTexture(WebGLRenderingContext.TEXTURE_2D, image.getTexture());
			mGlContext.uniform1i(mCurrentShader.getUTexture(), 0);
			
			mLastBoundTexture = image.getTexture();
		}

		setColor(image.getColor().r, image.getColor().g, image.getColor().b, image.getColor().a);
		mGlContext.uniform4fv(mCurrentShader.getUModulation(), mColor);
		
		setBlendMode(image.getBlendMode());
		
		enableTexCoords(true);
		
		dstRect.x += image.getOffsetX();
		dstRect.y += image.getOffsetY();
		
		float vertices[] = new float[8];
		PU_Rect imageTexCoords = image.getTextureCoords();
		if(imageTexCoords != null)
		{
			float scaleWidth = (float)dstRect.width/(float)image.getWidth();
			float scaleHeight = (float)dstRect.height/(float)image.getHeight();
			int trimmedWidth = (int)(scaleWidth * ((float)image.getWidth()-(float)imageTexCoords.width));
			int trimmedHeight = (int)(scaleHeight * ((float)image.getHeight()-(float)imageTexCoords.height));
			
			dstRect.width -= trimmedWidth;
			dstRect.height -= trimmedHeight;
		}

		vertices[0] = dstRect.x;
		vertices[1] = dstRect.y;
		vertices[2] = (dstRect.x + dstRect.width);
		vertices[3] = dstRect.y;
		vertices[4] = dstRect.x;
		vertices[5] = (dstRect.y + dstRect.height);
		vertices[6] = (dstRect.x + dstRect.width);
		vertices[7] = (dstRect.y + dstRect.height);
		
		WebGLBuffer buffer = mGlContext.createBuffer();
		mGlContext.bindBuffer(WebGLRenderingContext.ARRAY_BUFFER, buffer);
		mGlContext.bufferData(WebGLRenderingContext.ARRAY_BUFFER, Float32Array.create(vertices), WebGLRenderingContext.STREAM_DRAW);
		mGlContext.vertexAttribPointer(mCurrentShader.getAPosition(), 2, WebGLRenderingContext.FLOAT, false, 0, 0);
		
		float texCoords[] = new float[8];
		if(imageTexCoords != null)
		{
			srcRect.x += imageTexCoords.x;
			srcRect.y += imageTexCoords.y;
			
			float scaleWidth = (float)srcRect.width/(float)image.getWidth();
			float scaleHeight = (float)srcRect.height/(float)image.getHeight();
			int trimmedWidth = (int)(scaleWidth * ((float)image.getWidth()-(float)imageTexCoords.width));
			int trimmedHeight = (int)(scaleHeight * ((float)image.getHeight()-(float)imageTexCoords.height));
			
			srcRect.width -= trimmedWidth;
			srcRect.height -= trimmedHeight;
			
			texCoords[0] = (float)srcRect.x / (float)image.getTextureWidth();
			texCoords[1] = (float)srcRect.y / (float)image.getTextureHeight();
			texCoords[2] = ((float)srcRect.x + (float)srcRect.width) / (float)image.getTextureWidth();
			texCoords[3] = (float)srcRect.y / (float)image.getTextureHeight();
			texCoords[4] = (float)srcRect.x / (float)image.getTextureWidth();
			texCoords[5] = ((float)srcRect.y + (float)srcRect.height) / (float)image.getTextureHeight();
			texCoords[6] = ((float)srcRect.x + (float)srcRect.width) / (float)image.getTextureWidth();
			texCoords[7] = ((float)srcRect.y + (float)srcRect.height) / (float)image.getTextureHeight();
		}
		else
		{
			texCoords[0] = (float)srcRect.x / (float)image.getWidth();
			texCoords[1] = (float)srcRect.y / (float)image.getHeight();
			texCoords[2] = ((float)srcRect.x + (float)srcRect.width) / (float)image.getWidth();
			texCoords[3] = (float)srcRect.y / (float)image.getHeight();
			texCoords[4] = (float)srcRect.x / (float)image.getWidth();
			texCoords[5] = ((float)srcRect.y + (float)srcRect.height) / (float)image.getHeight();
			texCoords[6] = ((float)srcRect.x + (float)srcRect.width) / (float)image.getWidth();
			texCoords[7] = ((float)srcRect.y + (float)srcRect.height) / (float)image.getHeight();
		}

		buffer = mGlContext.createBuffer();
		mGlContext.bindBuffer(WebGLRenderingContext.ARRAY_BUFFER, buffer);
		mGlContext.bufferData(WebGLRenderingContext.ARRAY_BUFFER, Float32Array.create(texCoords), WebGLRenderingContext.STREAM_DRAW);
		mGlContext.vertexAttribPointer(mCurrentShader.getATexCoord(), 2, WebGLRenderingContext.FLOAT, false, 0, 0);
		
		mGlContext.drawArrays(WebGLRenderingContext.TRIANGLE_STRIP, 0, 4);
		mGlContext.flush();
	}
	
	public ImageElement getImageElement(final ImageResource imageResource)
	{
		ImageElement element = Document.get().createImageElement();
		element.setSrc(imageResource.getSafeUri().asString());
		return element;
	}
	
	public void beginSpriteBatch()
	{
		mSpriteBatchDrawCount = 0;
	}
	
	public void addToSpriteBatch(PU_Image image, int x, int y)
	{
		PU_Rect srcRect = image.getTextureCoords();
		PU_Rect dstRect = new PU_Rect(x, y, image.getWidth(), image.getHeight());
		dstRect.x += image.getOffsetX();
		dstRect.y += image.getOffsetY();
		
		dstRect.width = srcRect.width;
		dstRect.height = srcRect.height;
		
		int dataIdx = mSpriteBatchDrawCount * 16;
		
		if(dataIdx+32 > SPRITEBATCH_MAX_DATASIZE)
		{
			endSpriteBatch();
			beginSpriteBatch();
			
			dataIdx = 0;
		}
		
		if(mSpriteBatchDrawCount != 0)
		{
			float v = mSpriteBatchData[dataIdx-4];
			mSpriteBatchData[dataIdx] = v;
			dataIdx++;
			v = mSpriteBatchData[dataIdx-4];
			mSpriteBatchData[dataIdx] = v;
			dataIdx++;
			mSpriteBatchData[dataIdx++] = 0.0f;
			mSpriteBatchData[dataIdx++] = 0.0f;
			
			mSpriteBatchData[dataIdx++] = (float)dstRect.x;
			mSpriteBatchData[dataIdx++] = (float)dstRect.y;
			mSpriteBatchData[dataIdx++] = 0.0f;
			mSpriteBatchData[dataIdx++] = 0.0f;
			
			mSpriteBatchData[dataIdx++] = (float)dstRect.x;
			mSpriteBatchData[dataIdx++] = (float)dstRect.y;
			mSpriteBatchData[dataIdx++] = 0.0f;
			mSpriteBatchData[dataIdx++] = 0.0f;
			
			mSpriteBatchData[dataIdx++] = (float)dstRect.x;
			mSpriteBatchData[dataIdx++] = (float)dstRect.y;
			mSpriteBatchData[dataIdx++] = 0.0f;
			mSpriteBatchData[dataIdx++] = 0.0f;
			
			mSpriteBatchDrawCount++;
		}
		mSpriteBatchData[dataIdx++] = (float)dstRect.x;
		mSpriteBatchData[dataIdx++] = (float)dstRect.y;
		mSpriteBatchData[dataIdx++] = (float)srcRect.x / (float)image.getTextureWidth();
		mSpriteBatchData[dataIdx++] = (float)srcRect.y / (float)image.getTextureHeight();
		
		mSpriteBatchData[dataIdx++] = (float)(dstRect.x + dstRect.width);
		mSpriteBatchData[dataIdx++] = (float)dstRect.y;
		mSpriteBatchData[dataIdx++] = ((float)srcRect.x + (float)srcRect.width) / (float)image.getTextureWidth();
		mSpriteBatchData[dataIdx++] = (float)srcRect.y / (float)image.getTextureHeight();
		
		mSpriteBatchData[dataIdx++] = (float)dstRect.x;
		mSpriteBatchData[dataIdx++] = (float)(dstRect.y + dstRect.height);
		mSpriteBatchData[dataIdx++] = (float)srcRect.x / (float)image.getTextureWidth();
		mSpriteBatchData[dataIdx++] = ((float)srcRect.y + (float)srcRect.height) / (float)image.getTextureHeight();
		
		mSpriteBatchData[dataIdx++] = (float)(dstRect.x + dstRect.width);
		mSpriteBatchData[dataIdx++] = (float)(dstRect.y + dstRect.height);			
		mSpriteBatchData[dataIdx++] = ((float)srcRect.x + (float)srcRect.width) / (float)image.getTextureWidth();
		mSpriteBatchData[dataIdx++] = ((float)srcRect.y + (float)srcRect.height) / (float)image.getTextureHeight();
		
		mSpriteBatchDrawCount++;
	}
	
	public void endSpriteBatch()
	{
		useTextureShader();
		
		mGlContext.activeTexture(WebGLRenderingContext.TEXTURE0);
		mGlContext.bindTexture(WebGLRenderingContext.TEXTURE_2D, PUWeb.resources().getSpriteTexture());
		mGlContext.uniform1i(mCurrentShader.getUTexture(), 0);
		
		mLastBoundTexture = PUWeb.resources().getSpriteTexture();
		
		setColor(255, 255, 255, 255);
		mGlContext.uniform4fv(mCurrentShader.getUModulation(), mColor);
		
		setBlendMode(PU_Engine.BLENDMODE_BLEND);
		
		enableTexCoords(true);
		
		float[] mapData = new float[mSpriteBatchDrawCount * 16];
		System.arraycopy(mSpriteBatchData, 0, mapData, 0, mapData.length);
		
		WebGLBuffer buffer = mGlContext.createBuffer();
		mGlContext.bindBuffer(WebGLRenderingContext.ARRAY_BUFFER, buffer);
		mGlContext.bufferData(WebGLRenderingContext.ARRAY_BUFFER, Float32Array.create(mapData), WebGLRenderingContext.STREAM_DRAW);
		mGlContext.vertexAttribPointer(mCurrentShader.getAPosition(), 2, WebGLRenderingContext.FLOAT, false, 16, 0);
		mGlContext.vertexAttribPointer(mCurrentShader.getATexCoord(), 2, WebGLRenderingContext.FLOAT, false, 16, 8);

		mGlContext.drawArrays(WebGLRenderingContext.TRIANGLE_STRIP, 0, 4 * mSpriteBatchDrawCount);

		mGlContext.flush();
	}
}