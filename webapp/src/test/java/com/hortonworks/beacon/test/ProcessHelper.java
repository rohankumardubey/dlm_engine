package com.hortonworks.beacon.test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ProcessHelper {

    private static final Logger LOG = LoggerFactory.getLogger(ProcessHelper.class);

    public static Process startNew(String optionsAsString, String mainClass, String[] arguments) throws Exception {
        ProcessBuilder processBuilder = createProcess(optionsAsString, mainClass, arguments);
        Process process = processBuilder.start();
        LOG.info("Process started with arguments: {}", Arrays.toString(arguments));
        Thread.sleep(4000);// wait for the server to come up.
        return process;
    }

    private static ProcessBuilder createProcess(String optionsAsString, String mainClass, String[] arguments) {
        String jvm = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        String classpath = System.getProperty("java.class.path");
        String[] options = optionsAsString.split(" ");
        List < String > command = new ArrayList<>();
        command.add(jvm);
        command.addAll(Arrays.asList(options));
        command.add("-cp");
        command.add(classpath);
        command.add(mainClass);
        command.addAll(Arrays.asList(arguments));

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.inheritIO();
        return processBuilder;
    }

    public static void killProcess(Process process) throws Exception {
        if (process != null) {
            process.destroy();
        }
    }
}