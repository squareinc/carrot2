
/*
 * Carrot2 project.
 *
 * Copyright (C) 2002-2019, Dawid Weiss, Stanisław Osiński.
 * All rights reserved.
 *
 * Refer to the full license file "carrot2.LICENSE"
 * in the root folder of the repository checkout or at:
 * http://www.carrot2.org/carrot2.LICENSE
 */

package org.carrot2.core.test;

import static org.carrot2.core.test.SampleDocumentData.DOCUMENTS_DATA_MINING;
import static org.carrot2.core.test.assertions.Carrot2CoreAssertions.assertThatClusters;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.carrot2.core.*;
import org.carrot2.core.attribute.AttributeNames;
import org.carrot2.util.attribute.Bindable;
import org.carrot2.util.attribute.BindableMetadata;
import org.fest.assertions.Assertions;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;

import com.carrotsearch.randomizedtesting.annotations.Nightly;
import com.carrotsearch.randomizedtesting.annotations.ThreadLeakLingering;

import static org.junit.Assert.*;

/**
 * Simple baseline tests that apply to all clustering algorithms.
 */
public abstract class ClusteringAlgorithmTestBase<T extends IClusteringAlgorithm> 
    extends ProcessingComponentTestBase<T>
{
    /**
     * Algorithms are bindable, so their metadata should always be available.
     */
    @Test
    @Ignore
    @Deprecated
    public void testMetadataAvailable()
    {
        Class<? extends IClusteringAlgorithm> c = getComponentClass();
        Assume.assumeTrue(c.getAnnotation(Bindable.class) != null);
        
        BindableMetadata metadata = BindableMetadata.forClassWithParents(c);
        assertNotNull(metadata);
        assertNotNull(metadata.getAttributeMetadata());
    }

    /**
     * A test to check if the algorithm does not fail with no documents.
     */
    @Test
    public void testNoDocuments()
    {
        final Collection<Cluster> clusters = 
            cluster(Collections.<Document> emptyList()).getClusters();

        assertNotNull(clusters);
        assertEquals(0, clusters.size());
    }

    /**
     * @see "http://issues.carrot2.org/browse/CARROT-400"
     */
    @Test
    public void testEmptyDocuments()
    {
        final List<Document> documents = new ArrayList<>();
        final int documentCount = randomIntBetween(1, 100);
        for (int i = 0; i < documentCount; i++)
        {
            documents.add(new Document());
        }

        final List<Cluster> clusters = cluster(documents).getClusters();

        assertNotNull(clusters);
        assertEquals(1, clusters.size());
        assertThat(clusters.get(0).size()).isEqualTo(documentCount);
    }

    @Test
    public void testClusteringDataMining()
    {
        final ProcessingResult processingResult = cluster(DOCUMENTS_DATA_MINING);
        final Collection<Cluster> clusters = processingResult.getClusters();

        assertThat(clusters.size()).isGreaterThan(0);
    }

    /**
     * Performs a very simple stress test using a pooling {@link Controller}. The
     * test is performed with default init attributes.
     */
    @Nightly @Test 
    @ThreadLeakLingering(linger = 5000)
    public void testStress() throws InterruptedException, ExecutionException
    {
        final int numberOfThreads = randomIntBetween(1, 10);
        final int queriesPerThread = scaledRandomIntBetween(5, 25);

        /*
         * This yields a pooling controller effectively, because no cache interfaces are passed.
         */
        @SuppressWarnings("unchecked")
        final Controller controller = ControllerFactory.createPooling();
        controller.init(initAttributes);

        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        List<Callable<ProcessingResult>> callables = new ArrayList<>();
        for (int i = 0; i < numberOfThreads * queriesPerThread; i++)
        {
            final int dataSetIndex = i;
            callables.add(() -> {
                Map<String, Object> localAttributes = new HashMap<>();
                localAttributes.put(AttributeNames.DOCUMENTS, SampleDocumentData.ALL
                    .get(dataSetIndex % SampleDocumentData.ALL.size()));
                localAttributes.put("dataSetIndex", dataSetIndex);
                return controller.process(localAttributes, getComponentClass());
            });
        }

        try
        {
            List<Future<ProcessingResult>> results = executorService.invokeAll(callables);

            // Group results by query
            Map<Integer, List<ProcessingResult>> grouped = results.stream()
                .map(future -> {
                    try {
                        return future.get();
                    } catch (ExecutionException | InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                })
                .collect(Collectors.groupingBy(processingResult ->
                    (Integer) processingResult.getAttributes().get("dataSetIndex")));

            // Make sure results are the same within each data set
            for (List<ProcessingResult> e : grouped.values()) {
                List<List<Cluster>> clustering = e.stream().map(pr -> pr.getClusters()).collect(Collectors.toList());
                Iterator<List<Cluster>> iterator = clustering.iterator();
                if (!iterator.hasNext())
                {
                    continue;
                }

                final List<Cluster> firstClusterList = iterator.next();
                Assertions.assertThat(firstClusterList).isNotEmpty();
                while (iterator.hasNext())
                {
                    assertThatClusters(firstClusterList).isEquivalentTo(iterator.next());
                }
            }
        }
        finally
        {
            executorService.shutdown();
        }
    }

    /**
     * Performs clustering using {@link Controller}.
     * 
     * @param documents Documents to be clustered.
     * @return {@link ProcessingResult} returned from the controller.
     */
    public ProcessingResult cluster(Collection<Document> documents)
    {
        processingAttributes.put(AttributeNames.DOCUMENTS, documents);
        Controller controller = getSimpleController(initAttributes);
        try {
            ProcessingResult process = controller.process(processingAttributes, getComponentClass());
            return process;
        } finally {
            controller.dispose();
            super.simpleController = null;
        }
    }

    /**
     * Recursively collects documents from clusters.
     */
    public Collection<Document> collectDocuments(Collection<Cluster> clusters)
    {
        return collectDocuments(clusters, new HashSet<Document>());
    }

    /*
     * 
     */
    private Collection<Document> collectDocuments(Collection<Cluster> clusters,
        Collection<Document> documents)
    {
        for (final Cluster cluster : clusters)
        {
            documents.addAll(cluster.getDocuments());
            collectDocuments(cluster.getSubclusters());
        }

        return documents;
    }

    public static Set<String> collectClusterLabels(ProcessingResult pr)
    {
        final Set<String> clusterLabels = new HashSet<>();
        new Object()
        {
            public void dumpClusters(List<Cluster> clusters, int depth) 
            {
                for (Cluster c : clusters) {
                    clusterLabels.add(c.getLabel());
                    if (c.getSubclusters() != null) {
                        dumpClusters(c.getSubclusters(), depth + 1);
                    }
                }
            }
        }.dumpClusters(pr.getClusters(), 0);

        return clusterLabels;
    }
    
    public static void dumpClusterLabels(ProcessingResult pr)
    {
        new Object()
        {
            public void dumpClusters(List<Cluster> clusters, int depth) 
            {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < depth; i++) sb.append("  ");
                String indent = sb.toString();
                for (Cluster c : clusters) {
                    System.out.println(indent + c.getLabel());
                    if (c.getSubclusters() != null) {
                        dumpClusters(c.getSubclusters(), depth + 1);
                    }
                }
            }
        }.dumpClusters(pr.getClusters(), 0);
    }    
}