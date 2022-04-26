package org.example;

import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFmpegExecutor;
import net.bramp.ffmpeg.FFprobe;
import net.bramp.ffmpeg.builder.FFmpegBuilder;
import net.bramp.ffmpeg.probe.FFmpegFormat;
import net.bramp.ffmpeg.probe.FFmpegProbeResult;
import org.apache.commons.io.FilenameUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.util.Arrays;

/**
 * Hello world!
 */
public class App {

    public static final String[] allowedExtensions = {"mp4", "mkv", "avi", "mov"};

    public static void compressVideo(String inputFile, String outputFile, boolean deleteOriginal) {
        System.out.printf("Compressing video %s...%n", inputFile);

        String command = "ffprobe -v error -show_entries stream=width,height -of default=noprint_wrappers=1 \"%s\"".formatted(inputFile);

        //Getting the resolution of a video file
        String resolutions = getResolution(command);


        String width = resolutions.split("\n")[0].replace("width=", "").trim();
        String height = resolutions.split("\n")[1].replace("height=", "").trim();

        //Convert the width to an int
        int widthInt = Integer.parseInt(width);
        int heightInt = Integer.parseInt(height);

        //If the width is greater than the height, then the video is landscape, otherwise it's portrait. We store that value in a boolean (truth for landscape, false for portrait)
        boolean isLandscape = widthInt > heightInt;

        //If the video is landscape, and the width is higher than 1920, we set the width to 1920 and the height to 1080. Otherwise, the final values stay the same
        //Use the switch with the boolean value
        if (isLandscape) {
            if (widthInt > 1920) {
                widthInt = 1920;
                heightInt = 1080;
            }
        } else {
            if (heightInt > 1920) {
                widthInt = 1080;
                heightInt = 1920;
            }
        }

        //Print the resolution after conversion
        System.out.println(widthInt + "x" + heightInt);

        FFmpeg ffmpeg;
        FFprobe ffprobe;
        try {
            String ffmpegPath = findExecutableOnPath("ffmpeg");
            String ffprobePath = findExecutableOnPath("ffprobe");

            if (ffmpegPath.isEmpty() || ffprobePath.isEmpty()) {
                throw new IOException();
            }

            ffmpeg = new FFmpeg(ffmpegPath);
            ffprobe = new FFprobe(ffprobePath);
        } catch (IOException e) {
            String message = "Could not find ffmpeg or ffprobe on the path";
            return;
        }

        FFmpegBuilder builder = new FFmpegBuilder()
                .setInput(inputFile)
                .overrideOutputFiles(true)
                .addOutput(outputFile)
                .setFormat("mp4")
                .setVideoCodec("libx264")
                .setConstantRateFactor(25)
                .setVideoWidth(widthInt)
                .setVideoHeight(heightInt)
                .done();

        FFmpegExecutor executor = new FFmpegExecutor(ffmpeg, ffprobe);

        executor.createJob(builder).run();

        //If the size of the compressed video is greater than the original, we delete the compressed video
        if (new File(outputFile).length() > new File(inputFile).length()) {
            System.out.println("Compressed video is larger than original, trying to compress again...");

            builder = new FFmpegBuilder()
                    .setInput(inputFile)
                    .overrideOutputFiles(true)
                    .addOutput(outputFile)
                    .setFormat("mp4")
                    .setVideoCodec("libx264")
                    .setConstantRateFactor(30)
                    .setVideoWidth(widthInt)
                    .setVideoHeight(heightInt)
                    .done();

            executor.createJob(builder).run();
        }


        //Calculate the compression ratio as a double
        double compressionRatio = (double) new File(inputFile).length() / (double) new File(outputFile).length();

        //Calculate the absolute value of how far from 1 is the compressionRatio
        double difference = Math.abs(1 - compressionRatio);


        //Print "The compressed file is x time smaller than the original" as a double
        System.out.printf("The compressed file is %.2f times smaller than the original, (a %s difference)%n", compressionRatio, formatFileSize(new File(inputFile).length() - new File(outputFile).length()));


    }

    public static String formatFileSize(long size) {
        if (size <= 0) return "0";
        final String[] units = new String[]{"B", "kB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }


    public static String findExecutableOnPath(String name) {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec("where %s".formatted(name));
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            assert process != null;
            try (BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                File exePath = new File(in.readLine());

//                System.out.println("Successfully found path to " + name + ": " + exePath.getAbsolutePath());
                return exePath.getAbsolutePath();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return "";
    }

    private static String getResolution(String command) {
        java.util.Scanner s = null;

        try {
            s = new java.util.Scanner(Runtime.getRuntime().exec(command).getInputStream()).useDelimiter("\\A");
        } catch (IOException e) {
            e.printStackTrace();
        }

        assert s != null;
        return s.hasNext() ? s.next() : "";
    }

    public static void main(String[] args) {

        String videoFolder = "YOUR PATH HERE";
        String outputFolder = "YOUR PATH HERE";

        //Make sure both folders exist. If not, create them
        File videoFolderFile = new File(videoFolder);
        File outputFolderFile = new File(outputFolder);

        if (!videoFolderFile.exists()) {
            if (videoFolderFile.mkdir()) {
                System.out.println("Directory " + videoFolder + " created");
            }
        }
        if (!outputFolderFile.exists()) {
            if (outputFolderFile.mkdir()) {
                System.out.println("Directory " + outputFolder + " created");
            }
        }


        String[] allowedExtensions = {"mp4", "mkv", "avi", "mov"};

        //Get all the files in the folder
        File[] files = new File(videoFolder).listFiles();


        //Print the combined size of all the videos in the folder
        assert files != null;
        System.out.println("The combined size of all the videos in the folder is: " + formatFileSize(getTotalSize(files)));

        boolean deleteOriginal = false;

        for (File file : files) {
            //Check if the file is a video
            if (Arrays.asList(allowedExtensions).contains(FilenameUtils.getExtension(file.getName()))) {
                if (!FilenameUtils.getBaseName(file.getName()).contains("_compressed")) {
                    //Compress the video
                    String outputFile = outputFolder + "\\" + FilenameUtils.getBaseName(file.getName()) + "_compressed" + "." + FilenameUtils.getExtension(file.getName());
                    compressVideo(file.getAbsolutePath(), outputFile, deleteOriginal);
                }
            }
        }


    }

    private static long getTotalSize(File[] files) {
        //Only count the files that are videos
        long totalSize = 0;
        for (File file : files) {
            if (Arrays.asList(allowedExtensions).contains(FilenameUtils.getExtension(file.getName()))) {
                totalSize += file.length();
            }
        }
        return totalSize;
    }
}

