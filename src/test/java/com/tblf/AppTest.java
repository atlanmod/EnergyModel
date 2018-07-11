package com.tblf;

import com.tblf.utils.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

/**
 * Unit test for simple App.
 */
public class AppTest 
{
    private File directory;

    @Before
    public void setUp() throws IOException {
        directory = new File("src/test/resources/SimpleProject");

        org.apache.commons.io.FileUtils.deleteDirectory(directory);
        File file = new File("src/test/resources/SimpleProject.zip");
        FileUtils.unzip(file);

    }

    @Test
    public void checkMain() throws IOException, URISyntaxException {
        App.main(new String[]{directory.getAbsolutePath()});
    }

    @After
    public void tearDown() throws IOException {
        org.apache.commons.io.FileUtils.deleteDirectory(directory);
    }
}
