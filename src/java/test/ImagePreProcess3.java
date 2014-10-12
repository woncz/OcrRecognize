package test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.cookie.BasicClientCookie;

import util.StaticParamConstants;

public class ImagePreProcess3 {

	private static Map<BufferedImage, String> trainMap = null;
	private static int index = 0;

	public static int isBlack(int colorInt) {
		Color color = new Color(colorInt);
		if (color.getRed() + color.getGreen() + color.getBlue() <= 100) {
			return 1;
		}
		return 0;
	}

	public static int isWhite(int colorInt) {
		Color color = new Color(colorInt);
		if (color.getRed() + color.getGreen() + color.getBlue() > 600) {
			return 1;
		}
		return 0;
	}

	public static BufferedImage removeBackgroud(String picFile)
			throws Exception {
		BufferedImage img = ImageIO.read(new File(picFile));
		img = img.getSubimage(1, 1, img.getWidth() - 2, img.getHeight() - 2);
		int width = img.getWidth();
		int height = img.getHeight();
		double subWidth = (double) width / 5.0;
		for (int i = 0; i < 5; i++) {
			Map<Integer, Integer> map = new HashMap<Integer, Integer>();
			HashSet<Integer> colorWhite = new HashSet<Integer>();
			for (int x = (int) (1 + i * subWidth); x < (i + 1) * subWidth
					&& x < width - 1; ++x) {
				for (int y = 0; y < height; ++y) {
					if (isWhite(img.getRGB(x, y)) == 1)
						colorWhite.add(img.getRGB(x, y));
					if (isWhite(img.getRGB(x, y)) == 1)
						continue;
					if (map.containsKey(img.getRGB(x, y))) {
						map
								.put(img.getRGB(x, y), map
										.get(img.getRGB(x, y)) + 1);
					} else {
						map.put(img.getRGB(x, y), 1);
					}
				}
			}
			/**
			int max = 0;
			int colorMax = 0;
			for (Integer color : map.keySet()) {
				if (max < map.get(color)) {
					max = map.get(color);
					colorMax = color;
				}
			}
			System.out.println("colorMax:" + colorMax);
			*/

			for (int x = (int) (1 + i * subWidth); x < (i + 1) * subWidth
					&& x < width - 1; ++x) {
				for (int y = 0; y < height; ++y) {
					if (colorWhite.contains(img.getRGB(x, y))) {
						img.setRGB(x, y, Color.WHITE.getRGB());
					} else {
						img.setRGB(x, y, Color.BLACK.getRGB());
					}
				}
			}
		}
		return img;
	}

	public static BufferedImage removeBlank(BufferedImage img) throws Exception {
		int width = img.getWidth();
		int height = img.getHeight();
		int start = 0;
		int end = 0;
		Label1: for (int y = 0; y < height; ++y) {
			for (int x = 0; x < width; ++x) {
				if (isBlack(img.getRGB(x, y)) == 1) {
					start = y;
					break Label1;
				}
			}
		}
		Label2: for (int y = height - 1; y >= 0; --y) {
			for (int x = 0; x < width; ++x) {
				if (isBlack(img.getRGB(x, y)) == 1) {
					end = y;
					break Label2;
				}
			}
		}
		return img.getSubimage(0, start, width, end - start + 1);
	}

	public static List<BufferedImage> splitImage(BufferedImage img)
			throws Exception {
		List<BufferedImage> subImgs = new ArrayList<BufferedImage>();
		int width = img.getWidth();
		int height = img.getHeight();
		List<Integer> weightlist = new ArrayList<Integer>();
		for (int x = 0; x < width; ++x) {
			int count = 0;
			for (int y = 0; y < height; ++y) {
				if (isBlack(img.getRGB(x, y)) == 1) {
					count++;
				}
			}
			weightlist.add(count);
		}
		for (int i = 0; i < weightlist.size(); i++) {
			int length = 0;
			while (i < weightlist.size() && weightlist.get(i) > 0) {
				i++;
				length++;
			}
			if (length > 2) {
				subImgs.add(removeBlank(img.getSubimage(i - length, 0, length,
						height)));
			}
		}
		return subImgs;
	}

	public static Map<BufferedImage, String> loadTrainData() throws Exception {
		if (trainMap == null) {
			Map<BufferedImage, String> map = new HashMap<BufferedImage, String>();
			File dir = new File("train3");
			File[] files = dir.listFiles();
			for (File file : files) {
				map.put(ImageIO.read(file), file.getName().charAt(0) + "");
			}
			trainMap = map;
		}
		return trainMap;
	}

	public static String getSingleCharOcr(BufferedImage img,
			Map<BufferedImage, String> map) {
		String result = "#";
		int width = img.getWidth();
		int height = img.getHeight();
		int min = width * height;
		for (BufferedImage bi : map.keySet()) {
			int count = 0;
			if (Math.abs(bi.getWidth() - width) > 2)
				continue;
			int widthmin = width < bi.getWidth() ? width : bi.getWidth();
			int heightmin = height < bi.getHeight() ? height : bi.getHeight();
			Label1: for (int x = 0; x < widthmin; ++x) {
				for (int y = 0; y < heightmin; ++y) {
					if (isBlack(img.getRGB(x, y)) != isBlack(bi.getRGB(x, y))) {
						count++;
						if (count >= min)
							break Label1;
					}
				}
			}
			if (count < min) {
				min = count;
				result = map.get(bi);
			}
		}
		return result;
	}

	public static String getAllOcr(String file) throws Exception {
		BufferedImage img = removeBackgroud(file);
		List<BufferedImage> listImg = splitImage(img);
		Map<BufferedImage, String> map = loadTrainData();
		String result = "";
		for (BufferedImage bi : listImg) {
			result += getSingleCharOcr(bi, map);
		}
		ImageIO.write(img, "JPG", new File("result3//" + result + ".jpg"));
		return result;
	}

	public static void downloadImage() {
		/**
		 * HttpClient httpClient = new HttpClient(); GetMethod getMethod = new
		 * GetMethod("http://game.tom.com/checkcode.php"); for (int i = 0; i <
		 * 30; i++) { try { // 执行getMethod int statusCode =
		 * httpClient.executeMethod(getMethod); if (statusCode !=
		 * HttpStatus.SC_OK) { System.err.println("Method failed: " +
		 * getMethod.getStatusLine()); } // 读取内容 String picName = "img3//" + i +
		 * ".jpg"; InputStream inputStream =
		 * getMethod.getResponseBodyAsStream(); OutputStream outStream = new
		 * FileOutputStream(picName); IOUtils.copy(inputStream, outStream);
		 * outStream.close(); System.out.println(i + "OK!"); } catch (Exception
		 * e) { e.printStackTrace(); } finally { // 释放连接
		 * getMethod.releaseConnection(); } }
		 */
	}

	public static void trainData() throws Exception {
		File dir = new File("temp3");
		File[] files = dir.listFiles();
		for (File file : files) {
			//System.out.println(file.getName() + "----");
			BufferedImage img = removeBackgroud("temp3//" + file.getName());

			//ImageIO.write(img,"JPG", new File("train3//"+ file.getName() +".jpg"));

			List<BufferedImage> listImg = splitImage(img);
			if (listImg.size() == 4) {
				for (int j = 0; j < listImg.size(); ++j) {
					ImageIO.write(listImg.get(j), "JPG", new File("train3//"
							+ file.getName().charAt(j) + "-" + (index++)
							+ ".jpg"));
				}
			}
		}
	}

	public static void download(String fileName) {
		BasicClientCookie cookieUUID = new BasicClientCookie("P_UUID",
				"J3V1aWQnOid3YW5nY2hvbmd6aGknLCdjaXBoZXInOidoZWxsbzIwMTQn");
		cookieUUID.setVersion(0);
		cookieUUID.setDomain(StaticParamConstants.PMO_IP);
		cookieUUID.setPath("/ucas");
		cookieUUID.setExpiryDate(null);

		BasicCookieStore cookieStore = new BasicCookieStore();
		cookieStore.addCookie(cookieUUID);

		CloseableHttpClient httpClient = HttpClients.custom()
				.setDefaultCookieStore(cookieStore).build();
		HttpGet httpGet = new HttpGet(
				"http://" + StaticParamConstants.PMO_IP + "/ucas/user/auth/generator.htm");
		try {
			HttpResponse response = httpClient.execute(httpGet);
			System.out.println(response.getStatusLine());
			HttpEntity entity = response.getEntity();
			// 不能消费返回实体
			// EntityUtils.consume(entity);
			if (entity.getContentType().getValue().contains("image/jpeg")) {
				String picName = fileName;
				InputStream inputStream = entity.getContent();
				OutputStream outputStream = new FileOutputStream(picName);

				int i = 0;
				byte buffer[] = new byte[1024];
				while (true) {
					if (inputStream.available() < 1024) {
						while (i != -1) {
							i = inputStream.read();
							outputStream.write(i);
						}
						break;// 注意此处不能忘记哦
					} else {
						// 当文件的大小大于1024字节时
						inputStream.read(buffer);
						outputStream.write(buffer);
					}
				}

				inputStream.close();
				outputStream.close();
			}
		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		String fileName = "img//qq.jpg";
		download(fileName);
		trainData();
		String text = getAllOcr(fileName);
		System.out.println("ocr = " + text);
	}
}
