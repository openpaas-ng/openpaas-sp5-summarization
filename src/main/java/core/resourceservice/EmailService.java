/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package core.resourceservice;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import structures.resources.Email;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author pmeladianos
 */
public class EmailService extends resourceService {


    public List<Email> getEmails() {
        String query = getEmailServiceQuery(getQueries());
        SolrQuery solrQuery;
        solrQuery = new SolrQuery();
        if (query.startsWith(" AND")) {
            query = query.substring(4);
        }
        if (query.startsWith("AND")) {
            query = query.substring(3);
        }
        solrQuery.setQuery(query);
        if (!query.equals("")) {
            solrQuery.setHighlight(true).setHighlightSnippets(1).setHighlightSimplePost("</strong>").setHighlightSimplePre("<strong>"); //set other params as needed
            solrQuery.setParam("hl.fl", "content_t");
            solrQuery.setParam("hl.requireFieldMatch", "true");
        }

        solrQuery.setRows(15);
        String urlString = "http://195.251.252.8:8983/solr/mail";
        //SolrClient server = new HttpSolrClient.Builder(urlString).build();
        SolrClient server = new HttpSolrClient(urlString);
        QueryResponse response = null;
        try {
            response = server.query(solrQuery);

        } catch (Exception e) {
            System.out.println(e);
        }
        SolrDocumentList rs = response.getResults();
        long numFound = rs.getNumFound();
        int numResultsDisplay = (int) numFound;
        ArrayList<Email> items = new ArrayList<Email>();

        for (SolrDocument doc : rs) {
            String id = doc.get("messageId").toString();
            String from = doc.get("from").toString();
            String sentDate = doc.get("sentDate").toString();
            String subject = doc.get("subject").toString();
            String content = doc.get("content").toString();

            items.add(new Email(id, from, sentDate, subject, content));
        }
        return items;
    }

    private static String getEmailServiceQuery(Collection keywords) {
        StringBuilder query = new StringBuilder();
        for (Object key : keywords) {
            query.append(key.toString()).append(" OR ");
        }
        String query_0 = queryBuilder(query.toString(), "content");

        String query_1 = queryBuilder(query.toString(), "subject");
        query = new StringBuilder(query_0 + " OR " + query_1);
        return query.toString();
    }

    private static String queryBuilder(String s, String field) {
        String res;
        if (s.length() == 0) {
            res = field + ":*";
        } else {
            res = field + ":" + "'" + s + "'";
        }
        return res;

    }

}
