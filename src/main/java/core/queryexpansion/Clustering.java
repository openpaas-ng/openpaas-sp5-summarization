package core.queryexpansion;

import org.deeplearning4j.clustering.cluster.Cluster;
import org.deeplearning4j.clustering.cluster.ClusterSet;
import org.deeplearning4j.clustering.cluster.Point;
import org.deeplearning4j.clustering.kmeans.KMeansClustering;
import org.nd4j.linalg.api.ndarray.INDArray;
import service.Application;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Clustering {
    private static final int minClusterSize = 4, minQuerySize = 2, maxClusters = 3;
    private static final String distanceFunction = "cosinesimilarity";

    public static List<String> cluster(Set<String> vocab, Map<String, Double> topKeys, String language) {
        List<Point> pointsLst = new ArrayList<>();
        for (String word : vocab) {
            INDArray vector = Application.wordEmbeddings.getVector(word, language);
            if (vector != null) {
                pointsLst.add(new Point(word, vector));
            }
        }

        List<String> queries = new ArrayList<>();
        if (!pointsLst.isEmpty()) {
            ClusterSet cs = null;
            for (int jj = maxClusters; jj > 0; jj--) {
                KMeansClustering kmc = KMeansClustering.setup(jj, 20, distanceFunction);
                cs = kmc.applyTo(pointsLst);
                boolean clusterOK = evaluateClusters(cs.getClusters(), Math.min(minClusterSize, pointsLst.size()));
                if (clusterOK)
                    break;
            }
            queries.addAll(extractQueries(cs.getClusters(), topKeys));
        }
        return queries;
    }

    private static List<String> extractQueries(List<Cluster> clusters, Map<String, Double> topKeys) {
        List<String> queries = new ArrayList<>();
        for (Cluster c : clusters) {
            StringBuilder query = new StringBuilder();
            int counter = 0;
            for (Point p : c.getPoints()) {
                if (topKeys.keySet().contains(p.getId())) {
                    query.append(p.getId()).append(" ");
                    counter = counter + 1;
                }
            }
            if (counter > minQuerySize) {
                queries.add(query.toString().trim());
            }
        }
        return queries;
    }

    private static boolean evaluateClusters(List<Cluster> clusters, int minPoints) {
        for (Cluster c : clusters) {
            if (c.getPoints().size() < minPoints)
                return false;
        }
        return true;
    }
}
