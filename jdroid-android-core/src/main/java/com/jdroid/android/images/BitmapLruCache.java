package com.jdroid.android.images;

import android.graphics.Bitmap;
import androidx.collection.LruCache;

public class BitmapLruCache extends LruCache<String, Bitmap> {

	public BitmapLruCache(int maxSizeBytes) {
		super(maxSizeBytes);
	}

	@Override
	protected int sizeOf(String key, Bitmap value) {
		return value.getRowBytes() * value.getHeight();
	}
}
