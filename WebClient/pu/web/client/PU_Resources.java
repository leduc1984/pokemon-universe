package pu.web.client;

import java.util.HashMap;

import pu.web.client.resources.fonts.Fonts;
import pu.web.client.resources.gui.GuiImageBundle;
import pu.web.client.resources.tiles.Tiles;
import pu.web.shared.ImageLoadEvent;

import com.google.gwt.dom.client.ImageElement;
import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.resources.client.ResourcePrototype;
import com.google.gwt.xml.client.Document;
import com.google.gwt.xml.client.Element;
import com.google.gwt.xml.client.NodeList;
import com.google.gwt.xml.client.XMLParser;
import com.googlecode.gwtgl.binding.WebGLTexture;

public class PU_Resources
{
	private PU_Font[] mFonts = new PU_Font[Fonts.FONT_COUNT];
	private PU_Image[] mGuiImages = null;
	private HashMap<Integer, PU_Image> mTiles = new HashMap<Integer, PU_Image>();
	private HashMap<Long, PU_Image> mCreatureImages = new HashMap<Long, PU_Image>();
	
	private int mFontCount = 0;
	private int mFontCountLoaded = 0;
	
	private int mGuiImageCount = 0;
	private int mGuiImageCountLoaded = 0;
	
	private WebGLTexture mSpriteTexture = null;
	
	private boolean mSpritesLoaded = false;
	
	public PU_Resources()
	{
		mFontCount = Fonts.FONT_COUNT;
		mGuiImageCount = GuiImageBundle.INSTANCE.getResources().length;
	}
	
	public void checkComplete()
	{
		boolean complete = true;
		
		if(mFontCount != mFontCountLoaded)
			complete = false;
		
		if(mGuiImageCount != mGuiImageCountLoaded)
			complete = false;
		
		if(mSpritesLoaded)
			complete = false;
		
		if(complete)
			PUWeb.resourcesLoaded();
	}
	
	public int getFontLoadProgress()
	{
		return (int)((float)((float)mFontCountLoaded/(float)mFontCount)*100.0);
	}
	
	public int getGuiImageLoadProgress()
	{
		return (int)((float)((float)mGuiImageCountLoaded/(float)mGuiImageCount)*100.0);
	}
	
	public native boolean imageLoaded(ImageElement image) /*-{
		return image.complete;
	}-*/;
	
	public native void loadImage(ImageLoadEvent callback, ImageElement image) /*-{
		var events = this;
		
		if(image.complete)
		{
			callback.@pu.web.shared.ImageLoadEvent::loaded()();
		}
		else
		{
			image.addEventListener("load", function(e) {
				callback.@pu.web.shared.ImageLoadEvent::loaded()();
			}, false);
			
			image.addEventListener("error", function(e) {
				callback.@pu.web.shared.ImageLoadEvent::error()();
			}, false);
		}
	}-*/;
	
	public void loadFonts()
	{
		loadFont(Fonts.FONT_PURITAN_BOLD_14, (ImageResource) Fonts.INSTANCE.puritanBold14Bitmap(), Fonts.INSTANCE.puritanBold14Info().getText());
	}
	
	public void loadFont(final int fontId, ImageResource imageResource, final String fontInfo)
	{
		final WebGLTexture texture = PUWeb.engine().createEmptyTexture();
		final ImageElement image = PUWeb.engine().getImageElement(imageResource);
		loadImage(new ImageLoadEvent() 
		{
			@Override
			public void loaded()
			{
				PUWeb.engine().fillTexture(texture, image);
				setFont(fontId, new PU_Font(texture, fontInfo));
				
				mFontCountLoaded++;
				checkComplete();
			}

			@Override
			public void error()
			{
				mFontCountLoaded++;
				checkComplete();
				
			}
		}, image);
	}
	
	public PU_Font getFont(int fontId)
	{
		return mFonts[fontId];
	}
	
	public void setFont(int fontId, PU_Font font)
	{
		mFonts[fontId] = font;
	}
	
	public void loadGuiImages()
	{
		final ResourcePrototype[] resources = GuiImageBundle.INSTANCE.getResources();
		mGuiImages = new PU_Image[resources.length];
		for(ResourcePrototype resource : resources)
		{
			String name = resource.getName();
			final int id = Integer.parseInt(name.replace("res_", ""));
			
			final WebGLTexture texture = PUWeb.engine().createEmptyTexture();
			final ImageElement image = PUWeb.engine().getImageElement((ImageResource)resource);
			loadImage(new ImageLoadEvent() 
			{
				@Override
				public void loaded()
				{
					PUWeb.engine().fillTexture(texture, image);
					if(id >= 0 && id < resources.length)
					{
						mGuiImages[id] = new PU_Image(image.getWidth(), image.getHeight(), texture);
					}
					
					mGuiImageCountLoaded++;
					checkComplete();
				}

				@Override
				public void error()
				{
					mGuiImageCountLoaded++;
					checkComplete();
				}
			}, image);
		}
	}
	
	public PU_Image getGuiImage(int id)
	{
		if(mGuiImages != null && id >= 0 && id < mGuiImages.length)
		{
			return mGuiImages[id];
		}
		return null;
	}
	
	public void loadSprites()
	{
		final ImageResource imageResource = Tiles.INSTANCE.getTilesBitmap();
		final String imageInfo = Tiles.INSTANCE.getTilesInfo().getText();
	
		mSpriteTexture = PUWeb.engine().createEmptyTexture();
		final ImageElement image = PUWeb.engine().getImageElement(imageResource);
		loadImage(new ImageLoadEvent() 
		{
			@Override
			public void loaded()
			{
				PUWeb.engine().fillTexture(mSpriteTexture, image);
				
				Document infoDom = XMLParser.parse(imageInfo);
				
				NodeList sprites = infoDom.getElementsByTagName("sprite");
				for(int i = 0; i < sprites.getLength(); i++)
				{
					Element element = (Element) sprites.item(i);
					
					String name = element.getAttribute("n");
					
					PU_Rect texCoords = new PU_Rect();
					texCoords.x = Integer.parseInt(element.getAttribute("x"));
					texCoords.y = Integer.parseInt(element.getAttribute("y"));
					texCoords.width = Integer.parseInt(element.getAttribute("w"));
					texCoords.height = Integer.parseInt(element.getAttribute("h"));
					
					int offsetX = 0;
					if(element.hasAttribute("oX"))
						offsetX = Integer.parseInt(element.getAttribute("oX"));
					
					int offsetY = 0;
					if(element.hasAttribute("oY"))
						offsetY = Integer.parseInt(element.getAttribute("oY"));
					
					int width = texCoords.width;
					if(element.hasAttribute("oW"))
						width = Integer.parseInt(element.getAttribute("oW"));
					
					int height = texCoords.height;
					if(element.hasAttribute("oH"))
						height = Integer.parseInt(element.getAttribute("oH"));
					
					PU_Image spriteImage = new PU_Image(width, height, null);
					spriteImage.setTextureCoords(texCoords, image.getWidth(), image.getHeight());
					spriteImage.setOffsetX(offsetX);
					spriteImage.setOffsetY(offsetY);
					if(name.contains("creatures/"))
					{
						// Creature sprite
						parseCreatureSprite(name, spriteImage);
					}
					else
					{
						// Tile sprite
						parseTileSprite(name, spriteImage);
					}
					
				}
								
				mSpritesLoaded = true;
				checkComplete();
			}

			@Override
			public void error()
			{
				PUWeb.log("Error loading sprites");
				mSpritesLoaded = false;
				checkComplete();
			}
		}, image);
	}
	
	public WebGLTexture getSpriteTexture()
	{
		return mSpriteTexture;
	}
	
	public void parseTileSprite(String name, PU_Image image)
	{
		int id = Integer.parseInt(name);
		mTiles.put(id, image);
	}

	public void parseCreatureSprite(String name, PU_Image image)
	{
		String ids = name.replace("creatures/", "");
		String[] parts = ids.split("_");
		long bodypart = Long.parseLong(parts[0]);
		long id = Long.parseLong(parts[1]);
		long dir = Long.parseLong(parts[2]);
		long frame = Long.parseLong(parts[3]);
		
		long key = ((bodypart) | (id << 8) | (dir << 16) | (frame << 24));
		mCreatureImages.put(key, image);
	}
	
	public PU_Image getTileImage(int id)
	{
		return mTiles.get(id);
	}
	
	public PU_Image getCreatureImage(int bodypart, int id, int dir, int frame)
	{
		long key = ((bodypart) | (id << 8) | (dir << 16) | (frame << 24));
		return mCreatureImages.get(key);
	}
}
