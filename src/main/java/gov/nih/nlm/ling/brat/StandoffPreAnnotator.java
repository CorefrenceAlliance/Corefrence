package gov.nih.nlm.ling.brat;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import gov.nih.nlm.ling.core.Document;
import gov.nih.nlm.ling.core.Span;
import gov.nih.nlm.ling.core.SpanList;
import gov.nih.nlm.ling.util.FileUtils;

/**
 * A class with static methods to automatically annotate a text file with a pre-defined
 * list of annotation strings.
 * 
 * @author Halil Kilicoglu
 *
 */
public class StandoffPreAnnotator {
	private static Logger log = Logger.getLogger(StandoffPreAnnotator.class.getName());	
	
	/**
	 * Reads a pre-annotation file, which is expected to contain tab-delimited
	 * string/semantic type/count triples on each line.
	 * Lines beginning with # are ignored.
	 * 
	 * 
	 * @param file  the pre-annotation file to read
	 * @param anns  a <code>Map</code> of pre-annotation/type pairs, created by the method 
	 * @param cnts  a <code>Map</code> of pre-annotation/count pairs, created by the method
	 * 
	 * @throws IOException if there is a problem with reading <var>file</var>
	 */
	public static void readPreAnnFile(String file, Map<String,String> anns, Map<String,Integer> cnts) throws IOException {
		String strLine;
		List<String> lines = FileUtils.linesFromFile(file, StandoffAnnotationReader.DEFAULT_ENCODING);
		for (int i=0; i < lines.size(); i++) {
			strLine = lines.get(i);
			if (strLine.startsWith("#")) continue;
			String[] els = strLine.split("[\t]");
			String str = els[0];
			String type = els[1];
			int cnt = Integer.parseInt(els[2]);
			if (anns.containsKey(str)) continue;
			anns.put(str, type);
			cnts.put(str, cnt);
		}
	}
	
	private static int maxId(List<String> entityLines) {
		int max = -1;
		for (String t: entityLines) {
			 String[] tabbedStrs = t.split("[\t]");	
			 String ids = tabbedStrs[0];
			 int id = Integer.parseInt(ids.substring(1));
			 if (id > max) max = id;
		}
		return max;
	}
	
	private static Map<SpanList,String> createSpanMap(List<String> entityLines) {
		Map<SpanList,String> spanMap = new HashMap<>();
		for (String t: entityLines) {
			String[] tabbedStrs = t.split("[\t]");	
			String semSpan = tabbedStrs[1];
	    	String[] els = semSpan.split(StandoffAnnotationReader.FIELD_DELIMITER);
	    	String sem = els[0];
	    	String spStr = semSpan.substring(semSpan.indexOf(sem)+sem.length()+1);
	    	List<Span> sps = new ArrayList<Span>();
	    	// multiple spans
	    	if (spStr.indexOf(';') > 0) {
	    		String[] spss = spStr.split(";");
	    		for (int i=0; i < spss.length; i++) {
	    			Span si = new Span(spss[i],' ');
	    			sps.add(si);
	    		}
	    	} else  {
	    		Span si = new Span(spStr,' ');
				sps.add(si);
	    	}
	    	SpanList sp = new SpanList(sps);
	    	spanMap.put(sp, t);
		}
		return spanMap;
	}
	
	private static boolean overlappingSpan(Span span, Map<SpanList,String> spanMap ) {
		for (SpanList sp: spanMap.keySet()) {
			if (SpanList.overlap(sp, new SpanList(span))) return true;
		}
		return false;
	}
	
	
	/**
	 * Pre-annotates a text file with the annotations provided in <var>preAnns</var>.<p>
	 * If multiple pre-annotations are available for a given string (i.e., ambiguity),
	 * the most frequent pre-annotation will be used. This method currently only pre-annotates
	 * with term annotations. 
	 * 
	 * @param id  		the file identifier
	 * @param textFile  the text file to read
	 * @param annFile  	the existing annotation file corresponding to the text file 
	 * @param preAnns  	a <code>Map</code> of pre-annotations, generated by {@code #readPreAnnFile(String, Map, Map)} 
	 * @param preAnnCounts  a <code>Map</code> of pre-annotation counts, generated by {@code #readPreAnnFile(String, Map, Map)}
	 * @param outAnnFile  the file to write all annotations to
	 */
	public static void preAnnotate(String id, String textFile, String annFile, 
					final Map<String, String> preAnns, final Map<String,Integer> preAnnCounts, String outAnnFile) {
		List<String> newLines = new ArrayList<>();
		Document doc = StandoffAnnotationReader.readTextFile(id,textFile);
		Map<String,List<String>> annotationLines = StandoffAnnotationReader.readAnnotationFiles(Arrays.asList(annFile), null);
		// Currently, we are only pre-annotating entities
		int max = maxId(annotationLines.get("Term"));
		Map<SpanList,String> spanMap = createSpanMap(annotationLines.get("Term"));
		String docLowerCase = doc.getText().toLowerCase();
		List<String> preAnnStrs = new ArrayList<String>(preAnns.keySet());
		// sort by counts
		Collections.sort(preAnnStrs, new Comparator<String>() {
			public int compare(String a, String b) {
				int aTokNum = a.split("[ ]+").length;
				int bTokNum = b.split("[ ]+").length;
				if (bTokNum > aTokNum) return 1;
				if (aTokNum == bTokNum) {
					int aCnt = preAnnCounts.get(a);
					int bCnt = preAnnCounts.get(b);
					if (bCnt > aCnt) return 1;
					if (bCnt == aCnt) return 0;
				}
				return -1;
			}
		});
		for (String pre: preAnnStrs) {
			log.log(Level.FINE,"Adding pre-annotation: {0}.", new Object[]{pre});
			int lastIndex = 0;
			while(lastIndex != -1){
			       lastIndex =  docLowerCase.indexOf(pre,lastIndex);
			       if( lastIndex != -1){
			    	   Span span = new Span(lastIndex, lastIndex + pre.length());
		    		   char firstChar = docLowerCase.charAt(lastIndex);
		    		   char prevChar = '\n';
		    		   if (lastIndex > 0) prevChar = docLowerCase.charAt(lastIndex-1);
		    		   lastIndex = span.getEnd();
		    		   char lastChar = '\n';
		    		   if (lastIndex > 0) lastChar = docLowerCase.charAt(lastIndex-1);
			    	   char nextChar = docLowerCase.charAt(lastIndex);
			    	   if ((Character.isLetter(firstChar) && Character.isLetter(prevChar)) ||
			    			(Character.isLetter(lastChar) && Character.isLetter(nextChar))) {
			    			continue;
			    	   }
			    	   if (overlappingSpan(span,spanMap)) continue;

			    	   String nLine = "T"+ ++max + "\t" + preAnns.get(pre) + " " + 
		    			   	    span.toStandoffAnnotation() + "\t" +
		    			   	    doc.getText().substring(span.getBegin(), span.getEnd());
			    	   log.log(Level.FINE,"Adding pre-annotation line: {0}.", new Object[]{nLine});
			    	   newLines.add(nLine);
			           spanMap.put(new SpanList(span), nLine);
			      }
			}
		}
		PrintWriter pw = null;
		try {
			pw = new PrintWriter(outAnnFile);
			List<String> lines = FileUtils.linesFromFile(annFile, StandoffAnnotationReader.DEFAULT_ENCODING);
			for (String l: lines) {
				pw.println(l);
			}
			for (String n: newLines) {
				pw.println(n);
			}
		} catch (IOException e) {
			log.log(Level.SEVERE,"Unable to write pre-annotations to file: {0}.", new Object[]{outAnnFile});
			e.printStackTrace();
		} finally {
			pw.flush();
			pw.close();
		}
	}
	
	/**
	 * Pre-annotates all text files in a directory. 
	 * It expects the text files have the extension 'txt'. 
	 * 
	 * @param in		the input directory
	 * @param preAnns  	the pre-annotation map
	 * @param preAnnCounts  the pre-annotation counts
	 * @param out  the output directory
	 */
	public static void preAnnotateDirectory(String in, final Map<String, String> preAnns, final Map<String,Integer> preAnnCounts, String out) {
		File inDir = new File(in);
		if (!(inDir.isDirectory()))  {
			log.log(Level.SEVERE,"Input should be a directory: {0}. Skipping..", new Object[]{in});
			return;
		}
		File outDir = new File(out);
		if (!(outDir.isDirectory())) {
			log.log(Level.SEVERE,"Output should be a directory: {0}. Skipping..", new Object[]{out});
			return;
		}
		try {
			int fileNum = 0;
			List<String> files = FileUtils.listFiles(in, false, "txt");
			for (String filename: files) {
				String id = filename.replace(".txt", "");
				log.info("Processing " + id + ":" + ++fileNum);
				String txtFilename = inDir.getAbsolutePath() + File.separator + filename;
				String annFilename = inDir.getAbsolutePath() + File.separator + id + ".ann";
				String outFilename = outDir.getAbsolutePath() + File.separator + id + ".ann";
				StandoffPreAnnotator.preAnnotate(id,txtFilename,annFilename,preAnns,preAnnCounts,outFilename);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	public static void main(String[] args) throws IOException{
		String id = args[0];
		String textFile = args[1];
		String annFile = args[2];
		String preAnnFile = args[3];
		String outAnnFile = args[4];
		Map<String,String> preAnns = new HashMap<>();
		Map<String,Integer> preAnnCnts = new HashMap<>();
		readPreAnnFile(preAnnFile, preAnns, preAnnCnts);
		preAnnotate(id,textFile,annFile,preAnns,preAnnCnts,outAnnFile);
	}

}
