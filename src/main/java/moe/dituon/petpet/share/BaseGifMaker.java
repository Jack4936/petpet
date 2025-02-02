package moe.dituon.petpet.share;

import moe.dituon.petpet.share.FastAnimatedGifEncoder.FrameData;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BaseGifMaker {
    protected ExecutorService threadPool;

    /**
     * 默认线程池容量为 <b>CPU线程数 + 1</b>
     */
    public BaseGifMaker() {
        threadPool = Executors.newFixedThreadPool(BasePetService.DEFAULT_THREAD_POOL_SIZE);
    }

    public BaseGifMaker(int threadPoolSize) {
        threadPool = Executors.newFixedThreadPool(threadPoolSize);
    }

    public InputStream makeGIF(List<AvatarModel> avatarList, List<TextModel> textList,
                               Map<Short, BufferedImage> stickerMap, GifRenderParams params) {
        switch (params.getEncoder()) {
            case ANIMATED_LIB:
                return makeGifUseAnimatedLib(avatarList, textList, stickerMap, params);
            case BUFFERED_STREAM:
                return makeGifUseBufferedStream(avatarList, textList, stickerMap, params);
        }
        throw new RuntimeException();
    }

    public InputStream makeGifUseBufferedStream
            (List<AvatarModel> avatarList, List<TextModel> textList,
             Map<Short, BufferedImage> stickerMap, GifRenderParams params) {
        try {
            //遍历获取GIF长度(图片文件数量)
            short i = 0;
            CountDownLatch latch = new CountDownLatch(stickerMap.size());
            Map<Short, BufferedImage> imageMap = new HashMap<>(stickerMap.size());
            for (short key : stickerMap.keySet()) {
                short fi = i++;
                threadPool.execute(() -> {
                    BufferedImage image = ImageSynthesis.synthesisImage(
                            stickerMap.get(key), avatarList, textList,
                            params.getAntialias(), false, fi, params.getMaxSize()
                    );
                    BufferedImage temp =
                            new BufferedImage(image.getWidth(), image.getHeight(),
                                    BufferedImage.TYPE_3BYTE_BGR);
                    Graphics2D g = temp.createGraphics();
                    g.drawImage(image, 0, 0, null);
                    imageMap.put(fi, temp);
                    latch.countDown();
                });
            }
            BufferedGifEncoder gifEncoder =
                    new BufferedGifEncoder(BufferedImage.TYPE_3BYTE_BGR, params.getDelay(), true);
            latch.await();
            if (params.getReverse()) {
                var map = reverseMap(imageMap);
                for (i = 0; i < map.size(); i++) gifEncoder.addFrame(map.get(i));
            } else {
                for (i = 0; i < imageMap.size(); i++) gifEncoder.addFrame(imageMap.get(i));
            }
            gifEncoder.finish();
            return gifEncoder.getOutput();
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public InputStream makeGifUseAnimatedLib
            (List<AvatarModel> avatarList, List<TextModel> textList,
             Map<Short, BufferedImage> stickerMap, GifRenderParams params) {
        try {
            //遍历获取GIF长度(图片文件数量)
            short i = 0;
            CountDownLatch latch = new CountDownLatch(stickerMap.size());
            Map<Short, FrameData> frameMap = new HashMap<>(stickerMap.size());
            int[] size = new int[2];
            for (short key : stickerMap.keySet()) {
                short fi = i++;
                threadPool.execute(() -> {
                    BufferedImage image = ImageSynthesis.synthesisImage(
                            stickerMap.get(key), avatarList, textList,
                            params.getAntialias(), false, fi, params.getMaxSize()
                    );
                    BufferedImage temp =
                            new BufferedImage(image.getWidth(), image.getHeight(),
                                    BufferedImage.TYPE_3BYTE_BGR);
                    Graphics2D g = temp.createGraphics();
                    g.drawImage(image, 0, 0, null);
                    g.dispose();
                    FrameData frameData = new FrameData(temp, params.getQuality());
                    frameMap.put(fi, frameData);
                    if (fi == 0) {
                        size[0] = temp.getWidth();
                        size[1] = temp.getHeight();
                    }
                    latch.countDown();
                });
            }

            FastAnimatedGifEncoder gifEncoder = new FastAnimatedGifEncoder();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            gifEncoder.start(output);
            gifEncoder.setRepeat(0);
            gifEncoder.setDelay(params.getDelay());
            gifEncoder.setQuality(params.getQuality());

            latch.await();
            gifEncoder.setSize(size[0], size[1]);
            if (params.getReverse()) {
                var map = reverseMap(frameMap);
                map.forEach((id, frame) -> gifEncoder.addFrame(frame));
            } else {
                frameMap.forEach((id, frame) -> gifEncoder.addFrame(frame));
            }
            gifEncoder.finish();
            return new ByteArrayInputStream(output.toByteArray());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public InputStream makeGIF(List<AvatarModel> avatarList, List<TextModel> textList,
                               BufferedImage sticker, GifRenderParams params) {
        switch (params.getEncoder()) {
            case ANIMATED_LIB:
                return makeGifUseAnimatedLib(avatarList, textList, sticker, params);
            case BUFFERED_STREAM:
                return makeGifUseBufferedStream(avatarList, textList, sticker, params);
        }
        throw new RuntimeException();
    }

    private InputStream makeGifUseBufferedStream(
            List<AvatarModel> avatarList, List<TextModel> textList,
            BufferedImage sticker, GifRenderParams params) {
        try {
            short maxFrameLength = 1;
            for (AvatarModel avatar : avatarList) {
                maxFrameLength = (short) Math.max(maxFrameLength, avatar.getImageList().size());
            }

            CountDownLatch latch = new CountDownLatch(maxFrameLength);
            Map<Short, BufferedImage> imageMap = new HashMap<>(maxFrameLength);
            for (short i = 0; i < maxFrameLength; i++) {
                short fi = i;
                threadPool.execute(() -> {
                    BufferedImage image = ImageSynthesis.synthesisImage(
                            sticker, avatarList, textList,
                            params.getAntialias(), false, fi, params.getMaxSize()
                    );
                    BufferedImage temp =
                            new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
                    Graphics2D g = temp.createGraphics();
                    g.drawImage(image, 0, 0, null);
                    imageMap.put(fi, temp);
                    latch.countDown();
                });
            }

            BufferedGifEncoder gifEncoder =
                    new BufferedGifEncoder(BufferedImage.TYPE_3BYTE_BGR, params.getDelay(), true);
            latch.await();
            if (params.getReverse()) {
                var map = reverseMap(imageMap);
                for (short i = 0; i < map.size(); i++) gifEncoder.addFrame(map.get(i));
            } else {
                for (short i = 0; i < imageMap.size(); i++) gifEncoder.addFrame(imageMap.get(i));
            }
            gifEncoder.finish();
            return gifEncoder.getOutput();
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public InputStream makeGifUseAnimatedLib(
            List<AvatarModel> avatarList, List<TextModel> textList,
            BufferedImage sticker, GifRenderParams params) {
        try {
            short maxFrameLength = 1;
            for (AvatarModel avatar : avatarList) {
                maxFrameLength = (short) Math.max(maxFrameLength, avatar.getImageList().size());
            }

            CountDownLatch latch = new CountDownLatch(maxFrameLength);
            Map<Short, FrameData> frameMap = new HashMap<>(maxFrameLength);
            int[] size = new int[2];
            for (short i = 0; i < maxFrameLength; i++) {
                short fi = i;
                threadPool.execute(() -> {
                    BufferedImage image = ImageSynthesis.synthesisImage(
                            sticker, avatarList, textList,
                            params.getAntialias(), false, fi, params.getMaxSize()
                    );
                    BufferedImage temp =
                            new BufferedImage(image.getWidth(), image.getHeight(),
                                    BufferedImage.TYPE_3BYTE_BGR);
                    Graphics2D g = temp.createGraphics();
                    g.drawImage(image, 0, 0, null);
                    FrameData frameData = new FrameData(temp, params.getQuality());
                    frameMap.put(fi, frameData);
                    if (fi == 0) {
                        size[0] = temp.getWidth();
                        size[1] = temp.getHeight();
                    }
                    latch.countDown();
                });
            }

            FastAnimatedGifEncoder gifEncoder = new FastAnimatedGifEncoder();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            gifEncoder.start(output);
            gifEncoder.setDelay(params.getDelay());
            gifEncoder.setRepeat(0);
            gifEncoder.setQuality(params.getQuality());

            latch.await();
            gifEncoder.setSize(size[0], size[1]);
            if (params.getReverse()) {
                var map = reverseMap(frameMap);
                map.forEach((id, frame) -> gifEncoder.addFrame(frame));
            } else {
                frameMap.forEach((id, frame) -> gifEncoder.addFrame(frame));
            }
            gifEncoder.finish();
            return new ByteArrayInputStream(output.toByteArray());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    static private <T> Map<Short, T> reverseMap(Map<Short, T> originMap) {
        int size = originMap.size();
        Map<Short, T> map = new HashMap<>(originMap.size());
        originMap.forEach((id, img) -> map.put((short) (size - id - 1), img));
        return map;
    }
}