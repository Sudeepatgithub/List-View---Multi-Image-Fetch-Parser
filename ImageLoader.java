

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.naukriGulf.app.constants.CommonVars;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.view.View;
import android.widget.ImageView;

public class ImageLoader {

	private static final int stub_id = 0;
	private MemoryCache memoryCache = new MemoryCache();
	private FileCache fileCache;
	private Map<ImageView, String> imageViews = Collections
			.synchronizedMap(new WeakHashMap<ImageView, String>());
	private ExecutorService executorService;
	private Handler handler = new Handler();// handler to display images in UI
											// thread

	public ImageLoader(Context context) {
		fileCache = new FileCache(context);
		executorService = Executors.newFixedThreadPool(5);
	}

	public void displayImage(String url, ImageView imageView, View loadingView) {

		imageViews.put(imageView, url);
		Bitmap bitmap = memoryCache.get(url);
		if (bitmap != null) {
			imageView.setImageBitmap(bitmap);
			if (loadingView != null)
				loadingView.setVisibility(View.GONE);
		} else {
			queuePhoto(url, imageView, loadingView);
			imageView.setImageResource(stub_id);
		}
	}

	private void queuePhoto(String url, ImageView imageView, View defaultView) {
		PhotoToLoad photoToLoad = new PhotoToLoad(url, imageView, defaultView);
		executorService.submit(new PhotosLoader(photoToLoad));
	}

	private Bitmap getBitmap(String url) {
		File file = fileCache.getFile(url);

		// from SD cache
		Bitmap b = decodeFile(file);
		if (b != null)
			return b;

		// from web
		try {
			Bitmap bitmap = null;
			URL imageUrl = new URL(url);
			HttpURLConnection conn = (HttpURLConnection) imageUrl
					.openConnection();
			conn.setConnectTimeout(30000);
			conn.setReadTimeout(30000);
			conn.setInstanceFollowRedirects(true);
			InputStream is = conn.getInputStream();
			OutputStream os = new FileOutputStream(file);
			Util.copyStream(is, os);
			os.close();
			conn.disconnect();
			bitmap = decodeFile(file);
			return bitmap;
		} catch (Throwable throwable) {
			throwable.printStackTrace();
			if (throwable instanceof OutOfMemoryError)
				memoryCache.clear();
			return null;
		}
	}

	// decodes image and scales it to reduce memory consumption
	private Bitmap decodeFile(File file) {
		try {
			// decode image size
			BitmapFactory.Options o = new BitmapFactory.Options();
			o.inJustDecodeBounds = true;
			FileInputStream stream1 = new FileInputStream(file);
			BitmapFactory.decodeStream(stream1, null, o);
			stream1.close();

			// Find the correct scale value. It should be the power of 2.
			final int REQUIRED_SIZE = 70;
			int width_tmp = o.outWidth, height_tmp = o.outHeight;
			int scale = 1;
			while (true) {
				if (width_tmp / 2 < REQUIRED_SIZE
						|| height_tmp / 2 < REQUIRED_SIZE)
					break;
				width_tmp /= 2;
				height_tmp /= 2;
				scale *= 2;
			}

			// decode with inSampleSize
			BitmapFactory.Options o2 = new BitmapFactory.Options();
			o2.inSampleSize = scale;
			FileInputStream stream2 = new FileInputStream(file);
			Bitmap bitmap = BitmapFactory.decodeStream(stream2, null, o2);
			stream2.close();
			return bitmap;
		} catch (FileNotFoundException e) {
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	// Task for the queue
	private static class PhotoToLoad {
		public String url;
		public ImageView imageView;
		public View defaultLoadingView;

		public PhotoToLoad(String u, ImageView i, View defaultLoading) {
			url = u;
			imageView = i;
			this.defaultLoadingView = defaultLoading;
		}
	}

	private class PhotosLoader implements Runnable {
		PhotoToLoad photoToLoad;

		PhotosLoader(PhotoToLoad photoToLoad) {
			this.photoToLoad = photoToLoad;
		}

		@Override
		public void run() {
			try {
				if (imageViewReused(photoToLoad))
					return;
				Bitmap bitmap = getBitmap(photoToLoad.url);
				memoryCache.put(photoToLoad.url, bitmap);
				if (imageViewReused(photoToLoad))
					return;
				BitmapDisplayer bitmapDisplayer = new BitmapDisplayer(bitmap,
						photoToLoad);
				handler.post(bitmapDisplayer);
			} catch (Throwable th) {
				th.printStackTrace();
			}
		}
	}

	private boolean imageViewReused(PhotoToLoad photoToLoad) {
		String tag = imageViews.get(photoToLoad.imageView);
		if (tag == null || !tag.equals(photoToLoad.url))
			return true;
		return false;
	}

	// Used to display bitmap in the UI thread
	private class BitmapDisplayer implements Runnable {
		Bitmap bitmap;
		PhotoToLoad photoToLoad;

		public BitmapDisplayer(Bitmap bitmap, PhotoToLoad photoToLoad) {
			this.bitmap = bitmap;
			this.photoToLoad = photoToLoad;
		}

		public void run() {
			if (imageViewReused(photoToLoad))
				return;

			if (photoToLoad.defaultLoadingView != null) {
				photoToLoad.defaultLoadingView.setVisibility(View.GONE);
			}

			if (bitmap != null) {
				photoToLoad.imageView.setImageBitmap(bitmap);
			} else {
				Object tag = photoToLoad.imageView.getTag();
				if (CommonVars.COMPANY_BANNER_TAG.equals(tag)) {
					photoToLoad.imageView.setVisibility(View.GONE);
				} else {
					photoToLoad.imageView.setImageResource(stub_id);
				}
			}
		}
	}

/*	public void clearCache() {
		memoryCache.clear();
		fileCache.clear();
	}*/
}
