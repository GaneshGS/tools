/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.europeana.migration;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.CloudSolrServer;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.client.solrj.impl.LBHttpSolrServer;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.CursorMarkParams;

import com.mongodb.Mongo;
import com.mongodb.ServerAddress;

import eu.europeana.corelib.edm.exceptions.MongoDBException;
import eu.europeana.corelib.edm.utils.construct.FullBeanHandler;
import eu.europeana.corelib.edm.utils.construct.SolrDocumentHandler;
import eu.europeana.corelib.mongo.server.EdmMongoServer;
import eu.europeana.corelib.mongo.server.impl.EdmMongoServerImpl;

/**
 *
 * @author ymamakis
 */
public class Migration {

    private static HttpSolrServer sourceSolr;
    private static EdmMongoServer sourceMongo;

    private static CloudSolrServer targetSolr;
    private static EdmMongoServer targetMongo;
    private static FullBeanHandler mongoHandler;
    private static SolrDocumentHandler solrHandler;

    public static void main(String... args) {

        Mongo mongo;
        try {
            mongo = new Mongo("144.76.235.228", 27017);
            sourceSolr = new HttpSolrServer("http://144.76.235.228:9191/solr/search");
            sourceMongo = new EdmMongoServerImpl(mongo, "europeana", null, null);

            LBHttpSolrServer lbTarget = new LBHttpSolrServer("http://176.9.7.91:9191/solr", "http://176.9.7.182:9191/solr", "http://148.251.183.82:9191/solr", "http://78.46.60.203:9191/solr");
            targetSolr = new CloudSolrServer("176.9.7.91:2181", lbTarget);
            targetSolr.setDefaultCollection("search4");

            targetSolr.connect();
            List<ServerAddress> addresses = new ArrayList<>();
            ServerAddress address1 = new ServerAddress("176.9.7.91", 27017);
            ServerAddress address2 = new ServerAddress("176.9.7.182", 27017);
            ServerAddress address3 = new ServerAddress("148.251.183.82", 27017);
            ServerAddress address4 = new ServerAddress("78.46.60.203", 27017);
            addresses.add(address1);
            addresses.add(address2);
            addresses.add(address3);
            addresses.add(address4);
            Mongo tgtMongo = new Mongo(addresses);
            targetMongo = new EdmMongoServerImpl(tgtMongo, "europeana", null, null);
            mongoHandler = new FullBeanHandler(targetMongo);
            solrHandler = new SolrDocumentHandler(sourceSolr);

            String query = "*:*";
            String fl = "europeana_id";
            SolrQuery params = new SolrQuery();
            params.setQuery(query);
            params.setRows(10000);
            params.setSort(fl, SolrQuery.ORDER.asc);
            params.setFields(fl);
            String cursorMark = CursorMarkParams.CURSOR_MARK_START;
            if(new File("querymark").exists()){
            	cursorMark = FileUtils.readFileToString(new File("querymark"));
            }
            boolean done = false;
            int i = 0;
            while (!done) {
                params.set(CursorMarkParams.CURSOR_MARK_PARAM, cursorMark);
                QueryResponse resp = sourceSolr.query(params);
                String nextCursorMark = resp.getNextCursorMark();
                
                doCustomProcessingOfResults(resp);
                
                if (cursorMark.equals(nextCursorMark)) {
                    done = true;
                }
                cursorMark = nextCursorMark;
                FileUtils.write(new File("querymark"), cursorMark, false);
                i += 10000;
                Logger.getLogger(Migration.class.getName()).log(Level.INFO, "Added " + i + " documents");
            }

        } catch (UnknownHostException | MongoDBException | SolrServerException ex) {
            Logger.getLogger(Migration.class.getName()).log(Level.SEVERE, null, ex);
        } catch (MalformedURLException ex) {
            Logger.getLogger(Migration.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

    }

    private static void doCustomProcessingOfResults(QueryResponse resp) {

        if(resp.getResults().size()==10000){
        List<List<SolrDocument>> segments = segment(resp.getResults());
        CountDownLatch latch = new CountDownLatch(50);
        
        for(List<SolrDocument> segment:segments){
            ReadWriter writer = new ReadWriter();
            writer.setCloudServer(targetSolr);
            writer.setSegment(segment);
            writer.setSolrHandler(solrHandler);
            writer.setSourceMongo(sourceMongo);
            writer.setTargetMongo(targetMongo);
            writer.setfBeanHandler(mongoHandler);
            writer.setLatch(latch);
            Thread t = new Thread(writer);
            t.start();
        }
        try {
            latch.await();
        } catch (InterruptedException ex) {
            Logger.getLogger(Migration.class.getName()).log(Level.SEVERE, null, ex);
        }
        } else {
        	CountDownLatch latch = new CountDownLatch(1);
        	ReadWriter writer = new ReadWriter();
            writer.setCloudServer(targetSolr);
            writer.setSegment(resp.getResults());
            writer.setSolrHandler(solrHandler);
            writer.setSourceMongo(sourceMongo);
            writer.setTargetMongo(targetMongo);
            writer.setfBeanHandler(mongoHandler);
            writer.setLatch(latch);
            Thread t = new Thread(writer);
            t.start();
            try {
				latch.await();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        }
        
    }

    private static List<List<SolrDocument>> segment(SolrDocumentList results) {
        List<List<SolrDocument>> segments = new ArrayList<>();
        int i=0;
        int k=200;
        int iter = 50;
        while(i<iter*k){
            List<SolrDocument> segment = results.subList(i, i+k-1);
            segments.add(segment);
            i+=k;
        }
        return segments;
    }

    
    
    
}