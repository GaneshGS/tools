/* AatDumpImporter.java - created on 01/04/2016, Copyright (c) 2011 The European Library, all rights reserved */
package eu.europeana.vocab.getty.aat;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Consumer;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.openrdf.model.IRI;
import org.openrdf.model.Literal;
import org.openrdf.model.Statement;
import org.openrdf.repository.util.RDFRemover;
import org.openrdf.rio.RDFFormat;
import org.openrdf.rio.RDFHandler;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFParser;
import org.openrdf.rio.RDFParserFactory;
import org.openrdf.rio.RDFParserRegistry;
import org.openrdf.rio.helpers.RDFParserHelper;

import com.google.common.io.FileBackedOutputStream;

import eu.europeana.util.data.MapOfLists;

/**
 * 
 * 
 * @author Nuno Freire (nfreire@gmail.com)
 * @since 01/04/2016
 */
public class AatDumpImporter {
    
    static class LabelImporterRDFHandler implements RDFHandler {
        MapOfLists<String, Integer> labelMap=new MapOfLists<String, Integer>();

        HashMap<Integer, Integer> termToLabelMap=new HashMap<Integer, Integer>();        
        @Override
        public void startRDF() throws RDFHandlerException {
        }
        
        @Override
        public void handleStatement(Statement s) throws RDFHandlerException {
            if(s.getPredicate().getLocalName().equals("prefLabel")) {
                String idOfConcept = ((IRI)s.getSubject()).getLocalName();
//                labelMap.put(((IRI)s.getObject()).getLocalName(), Integer.parseInt(idOfConcept));
//                System.out.print(idOfConcept + " - ");
                
                String idOfTerm = ((IRI)s.getObject()).getLocalName();
//                System.out.println(idOfTerm );
                int langSepIdx = idOfTerm.indexOf("-");
                if (langSepIdx>0)
                    idOfTerm=idOfTerm.substring(0, langSepIdx);
                termToLabelMap.put(Integer.parseInt(idOfTerm), Integer.parseInt(idOfConcept));
            }else if(s.getPredicate().getLocalName().equals("literalForm")
                    && s.getObject() instanceof Literal) {
               Optional<String> language = ((Literal)s.getObject()).getLanguage();
               if(!language.isPresent()  || (language.isPresent() && language.get().startsWith("en"))) {
                   String idOfTerm = ((IRI)s.getSubject()).getLocalName();
                   idOfTerm=idOfTerm.substring(0, idOfTerm.indexOf("-"));
                   Integer idOfConcept = termToLabelMap.remove(Integer.parseInt(idOfTerm));
                   
                   if(idOfConcept!=null) {
                       String label = ((Literal)s.getObject()).getLabel().trim();
                       if(!label.startsWith("<") && !label.endsWith(">")) { 
                            List<Integer> containsKey = labelMap.get(label);
                            if( containsKey!=null && !containsKey.contains(idOfConcept))
                                   System.out.println("WARNING: AMBIGOUOS LABEL: " +label
                                           +" "+containsKey +" "
                                           +idOfConcept                                  );
                            
                            if( containsKey==null || !containsKey.contains(idOfConcept))
                                labelMap.put(label, idOfConcept);
        //                       System.out.println(((Literal)s.getObject()).getLabel().toLowerCase()+" -- "+idOfConcept);
                       }
                   }
               }
            }
        }
        
        @Override
        public void handleNamespace(String arg0, String arg1) throws RDFHandlerException {
        }
        
        @Override
        public void handleComment(String arg0) throws RDFHandlerException {
        }
        
        @Override
        public void endRDF() throws RDFHandlerException {
            System.out.println("Concept lables found: "+labelMap.size());
            System.out.println("Concept lables found all: "+labelMap.sizeOfAllLists());
            System.out.println("Terms not found: "+termToLabelMap.size());
        }

        /**
         * @param outCsvFile
         * @throws IOException 
         */
        public void exportLabelsMapToCsv(File outCsvFile) throws IOException {
            FileWriter f=new FileWriter(outCsvFile);
            CSVPrinter prt=new CSVPrinter(f, CSVFormat.EXCEL.withDelimiter(';'));
            for(String mapEntry: labelMap.keySet()) {
                prt.print(mapEntry);
                for(Integer aatId: labelMap.get(mapEntry))
                    prt.print(aatId);
                prt.println();
            }
            prt.close();
        }
    }
    
    public static void main(String[] args) throws Exception {
//        TripleStorage st=new TripleStorage(/c/AATOut_1Subjects.nt"), null, RDFFormat.NTRIPLES);
        
        RDFParserFactory parserFact = RDFParserRegistry.getInstance().get(RDFFormat.NTRIPLES).get();
        RDFParser parser = parserFact.getParser();
        LabelImporterRDFHandler labelImporter = new LabelImporterRDFHandler();
        parser.setRDFHandler(labelImporter);
        parser.parse(new FileInputStream(new File("src/AATOut_2Terms.nt")), "");
            
        labelImporter.exportLabelsMapToCsv(new File("AAT_Labels_To_Ids_Map.csv"));
    }
}
