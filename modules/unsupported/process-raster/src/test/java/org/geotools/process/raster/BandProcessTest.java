package org.geotools.process.raster;

import static org.junit.Assert.assertEquals;

import java.awt.Rectangle;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.media.jai.ROI;
import javax.media.jai.RenderedOp;
import javax.media.jai.operator.CropDescriptor;
import javax.media.jai.operator.MeanDescriptor;
import javax.media.jai.operator.SubtractDescriptor;
import javax.media.jai.operator.TranslateDescriptor;

import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.io.AbstractGridFormat;
import org.geotools.coverage.grid.io.GridFormatFinder;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geometry.Envelope2D;
import org.geotools.geometry.jts.JTS;
import org.geotools.resources.coverage.CoverageUtilities;
import org.geotools.test.TestData;
import org.jaitools.imageutils.ROIGeometry;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opengis.coverage.grid.GridCoverageReader;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.geometry.MismatchedDimensionException;
import org.opengis.metadata.spatial.PixelOrientation;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Polygon;

/**
 * This class is used for testing the processes operating on the bands: BandMerge and BandSelect.
 * 
 * @author Nicola Lagomarsini, GeoSolutions S.A.S.
 * 
 */
public class BandProcessTest {

    /** Tolerance value for the double comparison */
    private static final double TOLERANCE = 0.1d;

    /** First coverage to use */
    private static GridCoverage2D coverage1;

    /** Second coverage to use, equal to the first coverage */
    private static GridCoverage2D coverage2;

    /** Third coverage to use, which equal to the first one but also translated on the X direction */
    private static GridCoverage2D coverage3;

    @BeforeClass
    public static void setup() throws FileNotFoundException, IOException,
            NoninvertibleTransformException {
        // First file selection
        File input = TestData.file(BandProcessTest.class, "sample.tif");
        AbstractGridFormat format = GridFormatFinder.findFormat(input);
        // Get a reader for the selected format
        GridCoverageReader reader = format.getReader(input);
        // Read the input Coverage
        coverage1 = (GridCoverage2D) reader.read(null);
        // Reader disposal
        reader.dispose();

        // Second File selection
        coverage2 = coverage1;

        // Third file selection (This file is similar to the first one but it is translated by 1 on the X direction)
        File input2 = TestData.file(BandProcessTest.class, "sample2.tif");
        format = GridFormatFinder.findFormat(input2);
        // Get a reader for the selected format
        reader = format.getReader(input2);
        // Read the input Coverage
        coverage3 = (GridCoverage2D) reader.read(null);
        // Reader disposal
        reader.dispose();
    }

    // Ensure that the merging and selecting two equal images returns the same images
    @Test
    public void testEqualImages() {
        // Creation of a GridCoverage List
        List<GridCoverage2D> coverages = new ArrayList<GridCoverage2D>();
        coverages.add(coverage1);
        coverages.add(coverage2);

        // ///////////////
        //
        // BANDMERGE STEP
        //
        // ///////////////

        // Call of the bandmerge process
        BandMergeProcess bandmerge = new BandMergeProcess();
        // Execution of the process (used Last coverage g2w transformation)
        GridCoverage2D merged = bandmerge.execute(coverages, null, "last", null);

        // Checks on the new Coverage
        Assert.assertEquals(2, merged.getNumSampleDimensions());

        // Input Coverage BBOX
        Envelope2D sourceEnv = coverage1.getEnvelope2D();

        // Merged Coverage BBOX
        Envelope2D mergedEnv = merged.getEnvelope2D();

        // Ensure same BBOX
        assertEqualBBOX(sourceEnv, mergedEnv);

        // Selection of the Images associated to each coverage
        RenderedImage mergedImg = merged.getRenderedImage();
        RenderedImage srcImg1 = coverage1.getRenderedImage();
        RenderedImage srcImg2 = coverage2.getRenderedImage();

        // Ensure they have the same dimensions
        assertEqualImageDim(mergedImg, srcImg1);
        assertEqualImageDim(mergedImg, srcImg2);

        // ///////////////
        //
        // BANDSELECT STEP
        //
        // ///////////////

        // Call of the bandselect process
        BandSelectProcess bandselect = new BandSelectProcess();

        // Selection of the first coverage
        GridCoverage2D selected1 = bandselect.execute(merged, new int[] { 0 }, null);

        // Selection of the second coverage
        GridCoverage2D selected2 = bandselect.execute(merged, new int[] { 1 }, null);

        // Check the sample dimensions number
        assertEquals(1, selected1.getNumSampleDimensions());
        assertEquals(1, selected2.getNumSampleDimensions());

        // Check the BBOX
        assertEqualBBOX(mergedEnv, selected1.getEnvelope2D());
        assertEqualBBOX(mergedEnv, selected2.getEnvelope2D());

        // Ensure they have the same dimensions
        assertEqualImageDim(mergedImg, selected1.getRenderedImage());
        assertEqualImageDim(mergedImg, selected2.getRenderedImage());

        // Ensure that the images are equals to the source
        ensureEqualImages(srcImg1, selected1.getRenderedImage());
        ensureEqualImages(srcImg2, selected2.getRenderedImage());
    }

    // Ensure that the merging and selecting two equal images with ROI returns the same images
    // with nodata outside the ROI
    @Test
    public void testROI() throws IOException, MismatchedDimensionException, TransformException {
        // Creation of a GridCoverage List
        List<GridCoverage2D> coverages = new ArrayList<GridCoverage2D>();
        coverages.add(coverage1);
        coverages.add(coverage2);

        // Creation of a Geometry to use as ROI
        SimpleFeature feature = createFeature(coverage1);

        // ///////////////
        //
        // BANDMERGE STEP
        //
        // ///////////////

        // Call of the bandmerge process
        BandMergeProcess bandmerge = new BandMergeProcess();
        // Execution of the process
        GridCoverage2D merged = bandmerge.execute(coverages, feature, null, null);

        // Checks on the new Coverage
        Assert.assertEquals(2, merged.getNumSampleDimensions());

        // Input Coverage BBOX
        Envelope2D sourceEnv = coverage1.getEnvelope2D();

        // Merged Coverage BBOX
        Envelope2D mergedEnv = merged.getEnvelope2D();

        // Ensure same BBOX
        assertEqualBBOX(sourceEnv, mergedEnv);

        // Selection of the Images associated to each coverage
        RenderedImage mergedImg = merged.getRenderedImage();
        RenderedImage srcImg1 = coverage1.getRenderedImage();
        RenderedImage srcImg2 = coverage2.getRenderedImage();

        // Ensure they have the same dimensions
        assertEqualImageDim(mergedImg, srcImg1);
        assertEqualImageDim(mergedImg, srcImg2);

        // ///////////////
        //
        // BANDSELECT STEP
        //
        // ///////////////

        // Call of the bandselect process
        BandSelectProcess bandselect = new BandSelectProcess();

        // Selection of the first coverage
        GridCoverage2D selected1 = bandselect.execute(merged, new int[] { 0 }, null);

        // Selection of the second coverage
        GridCoverage2D selected2 = bandselect.execute(merged, new int[] { 1 }, null);

        // Check the sample dimensions number
        assertEquals(1, selected1.getNumSampleDimensions());
        assertEquals(1, selected2.getNumSampleDimensions());

        // Check the BBOX
        assertEqualBBOX(mergedEnv, selected1.getEnvelope2D());
        assertEqualBBOX(mergedEnv, selected2.getEnvelope2D());

        // Ensure they have the same dimensions
        assertEqualImageDim(mergedImg, selected1.getRenderedImage());
        assertEqualImageDim(mergedImg, selected2.getRenderedImage());

        // Ensure equal images inside ROI. This requires cropping the images
        CropCoverage crop = new CropCoverage();
        RenderedImage cropSrc = crop.execute(coverage1, (Geometry) feature.getDefaultGeometry(),
                null).getRenderedImage();

        RenderedImage cropSel1 = crop.execute(selected1, (Geometry) feature.getDefaultGeometry(),
                null).getRenderedImage();

        RenderedImage cropSel2 = crop.execute(selected2, (Geometry) feature.getDefaultGeometry(),
                null).getRenderedImage();

        // Ensure that the images are equals on the cropped selection
        ensureEqualImages(cropSrc, cropSel1);
        ensureEqualImages(cropSrc, cropSel2);

        // Ensure that the images contain No Data outside of the ROI bounds
        ensureNoDataOutside(selected1, feature);
        ensureNoDataOutside(selected2, feature);
    }

    // Ensure that the merging and selecting two images where one of them is translated, returns the two images, each of the placed
    // by taking into account their position inside the global image envelope
    @Test
    public void testDifferentImages() {
        // Creation of a GridCoverage List
        List<GridCoverage2D> coverages = new ArrayList<GridCoverage2D>();
        coverages.add(coverage1);
        coverages.add(coverage3);

        // ///////////////
        //
        // BANDMERGE STEP
        //
        // ///////////////

        // Call of the bandmerge process
        BandMergeProcess bandmerge = new BandMergeProcess();
        // Execution of the process
        GridCoverage2D merged = bandmerge.execute(coverages, null, null, null);

        // Checks on the new Coverage
        Assert.assertEquals(2, merged.getNumSampleDimensions());
        // Ensure that the final Envelope is expanded
        Envelope2D cov1Env = coverage1.getEnvelope2D();
        Envelope2D cov3Env = coverage3.getEnvelope2D();
        // Global coverage creation
        Envelope2D globalEnv = new Envelope2D(cov1Env);
        globalEnv.include(cov3Env);

        assertEqualBBOX(globalEnv, merged.getEnvelope2D());

        // ///////////////
        //
        // BANDSELECT STEP
        //
        // ///////////////

        // Call of the bandselect process
        BandSelectProcess bandselect = new BandSelectProcess();

        // Selection of the first coverage
        GridCoverage2D selected1 = bandselect.execute(merged, new int[] { 0 }, null);

        // Selection of the second coverage
        GridCoverage2D selected2 = bandselect.execute(merged, new int[] { 1 }, null);

        // Check the sample dimensions number
        assertEquals(1, selected1.getNumSampleDimensions());
        assertEquals(1, selected2.getNumSampleDimensions());

        // Check the BBOX
        assertEqualBBOX(globalEnv, selected1.getEnvelope2D());
        assertEqualBBOX(globalEnv, selected2.getEnvelope2D());

        // Initial image
        RenderedImage srcImg = coverage1.getRenderedImage();
        // Final images
        RenderedImage sel1 = selected1.getRenderedImage();
        RenderedImage sel2 = selected2.getRenderedImage();

        // Since we know that the first image is on the left side of the envelope and the
        // second on the right side, we crop and translate the images in order to
        // compare the initial image with them.

        // First image requires only cropping since it is on the right position
        RenderedOp crop1 = CropDescriptor.create(sel1, 0f, 0f, (float) srcImg.getWidth(),
                (float) srcImg.getHeight(), null);
        // Minimum
        float minX = sel1.getWidth() - srcImg.getWidth();
        float minY = sel2.getHeight() - srcImg.getHeight();
        // Cropping + Translation of the second image
        RenderedOp crop2 = CropDescriptor.create(sel2, minX, minY, (float) srcImg.getWidth(),
                (float) srcImg.getHeight(), null);
        crop2 = TranslateDescriptor.create(crop2, -minX, -minY, null, null);

        // Final check on the images
        ensureEqualImages(srcImg, crop1);
        ensureEqualImages(srcImg, crop2);
    }

    /**
     * Ensure that the input Images have the same dimensions
     * 
     * @param mergedImg
     * @param srcImg1
     */
    private void assertEqualImageDim(RenderedImage mergedImg, RenderedImage srcImg1) {
        Assert.assertEquals(mergedImg.getMinX(), srcImg1.getMinX());
        Assert.assertEquals(mergedImg.getMinY(), srcImg1.getMinY());
        Assert.assertEquals(mergedImg.getWidth(), srcImg1.getWidth());
        Assert.assertEquals(mergedImg.getHeight(), srcImg1.getHeight());
    }

    /**
     * Method for checking that two bounding box are equals
     * 
     * @param sourceEnv
     * @param dstEnv
     */
    private void assertEqualBBOX(Envelope2D sourceEnv, Envelope2D dstEnv) {
        double srcX = sourceEnv.x;
        double srcY = sourceEnv.y;
        double srcW = sourceEnv.width;
        double srcH = sourceEnv.height;

        double dstX = dstEnv.x;
        double dstY = dstEnv.y;
        double dstW = dstEnv.width;
        double dstH = dstEnv.height;

        Assert.assertEquals(srcX, dstX, TOLERANCE);
        Assert.assertEquals(srcY, dstY, TOLERANCE);
        Assert.assertEquals(srcW, dstW, TOLERANCE);
        Assert.assertEquals(srcH, dstH, TOLERANCE);
    }

    /**
     * Method for checking that two images are equal
     * 
     * @param source0
     * @param source1
     */
    private void ensureEqualImages(RenderedImage source0, RenderedImage source1) {
        // Subtraction between the two images
        RenderedOp sub = SubtractDescriptor.create(source0, source1, null);
        // Calculation of the mean value of the subtraction operation
        RenderedOp stats = MeanDescriptor.create(sub, null, 1, 1, null);
        // Get the mean
        double mean = ((double[]) stats.getProperty("mean"))[0];
        // Check that the mean value is 0
        assertEquals(0, mean, TOLERANCE);
    }

    /**
     * Method for creating the SimpleFeature to test
     * 
     * @param coverage
     * @return
     */
    private SimpleFeature createFeature(GridCoverage2D coverage) {
        // Selection of the Envelope associated to the Coverage
        Envelope2D envelope = coverage.getEnvelope2D();

        // Geometry creation
        GeometryFactory fact = new GeometryFactory();
        Coordinate[] coordinates = new Coordinate[5];
        // Populating the Coordinate array in order to create the Polygon
        for (int i = 0; i < coordinates.length; i++) {

            switch (i) {
            case 0:
            case 4:
                coordinates[i] = new Coordinate(envelope.getMinX(), envelope.getMinY());
                break;
            case 1:
                coordinates[i] = new Coordinate(envelope.getMinX(), envelope.getMinY()
                        + envelope.getHeight() / 2);
                break;
            case 2:
                coordinates[i] = new Coordinate(envelope.getMinX() + envelope.getWidth() / 2,
                        envelope.getMinY() + envelope.getHeight() / 2);
                break;
            case 3:
                coordinates[i] = new Coordinate(envelope.getMinX() + envelope.getWidth() / 2,
                        envelope.getMinY());
                break;
            }
        }
        // polygon creation from the coordinate array
        Polygon poly = fact.createPolygon(coordinates);

        // SimpleFeature creation
        SimpleFeatureTypeBuilder b = new SimpleFeatureTypeBuilder();

        // set the name
        b.setName("type");
        b.add("location", Polygon.class); // then add geometry

        // build the type
        final SimpleFeatureType featureType = b.buildFeatureType();
        // Builder for the feature
        SimpleFeatureBuilder builder = new SimpleFeatureBuilder(featureType);
        builder.add(poly);
        // Build feature
        return builder.buildFeature("id");
    }

    /**
     * Method for checking if the coverage values outside ROI are NoData
     * 
     * @param coverage
     * @param feature
     * @throws MismatchedDimensionException
     * @throws TransformException
     */
    private void ensureNoDataOutside(GridCoverage2D coverage, SimpleFeature feature)
            throws MismatchedDimensionException, TransformException {
        // World to Grid transform used to project the Geometry to the RasterSpace
        MathTransform w2g = coverage.getGridGeometry().getCRSToGrid2D(PixelOrientation.UPPER_LEFT);
        // ROI in raster space
        ROI roi = new ROIGeometry(JTS.transform((Geometry) feature.getDefaultGeometry(), w2g));
        // Approximate bounds by expanding them by one (coordinates are taken with a 0.5 offset)
        Rectangle rect = roi.getBounds();
        rect.grow(1, 1);
        // No data value to use
        double nodata = CoverageUtilities.getBackgroundValues(coverage)[0];

        // Cycle on the image Bounds in order to search if No Data are present
        RenderedImage img = coverage.getRenderedImage();
        // Tile bounds
        int minTileX = img.getMinTileX();
        int minTileY = img.getMinTileY();
        int maxTileX = minTileX + img.getNumXTiles();
        int maxTileY = minTileY + img.getNumYTiles();

        // Cycle on the tiles
        for (int tx = minTileX; tx < maxTileX; tx++) {
            for (int ty = minTileY; ty < maxTileY; ty++) {
                Raster tile = img.getTile(tx, ty);

                // Cycle on the tile values
                int minx = tile.getMinX();
                int miny = tile.getMinY();

                int maxx = minx + tile.getWidth();
                int maxy = miny + tile.getHeight();
                // Check each pixel outside the ROI
                for (int x = minx; x < maxx; x++) {
                    for (int y = miny; y < maxy; y++) {
                        if (!rect.contains(x, y)) {
                            assertEquals(nodata, tile.getSampleDouble(x, y, 0), TOLERANCE);
                        }
                    }
                }
            }
        }
    }
}
