package rres.knetminer.datasource.ondexlocal.service;

import static java.util.stream.Collectors.toMap;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.stereotype.Component;

import net.sourceforge.ondex.InvalidPluginArgumentException;
import net.sourceforge.ondex.ONDEXPluginArguments;
import net.sourceforge.ondex.algorithm.graphquery.nodepath.EvidencePathNode;
import net.sourceforge.ondex.args.FileArgumentDefinition;
import net.sourceforge.ondex.config.ONDEXGraphRegistry;
import net.sourceforge.ondex.core.Attribute;
import net.sourceforge.ondex.core.AttributeName;
import net.sourceforge.ondex.core.ConceptAccession;
import net.sourceforge.ondex.core.ConceptClass;
import net.sourceforge.ondex.core.ConceptName;
import net.sourceforge.ondex.core.EntityFactory;
import net.sourceforge.ondex.core.EvidenceType;
import net.sourceforge.ondex.core.MetaDataFactory;
import net.sourceforge.ondex.core.ONDEXConcept;
import net.sourceforge.ondex.core.ONDEXGraph;
import net.sourceforge.ondex.core.ONDEXGraphMetaData;
import net.sourceforge.ondex.core.ONDEXRelation;
import net.sourceforge.ondex.core.RelationType;
import net.sourceforge.ondex.core.memory.MemoryONDEXGraph;
import net.sourceforge.ondex.core.searchable.LuceneConcept;
import net.sourceforge.ondex.core.searchable.ScoredHits;
import net.sourceforge.ondex.core.util.ONDEXGraphUtils;
import net.sourceforge.ondex.export.cyjsJson.Export;
import net.sourceforge.ondex.filter.unconnected.ArgumentNames;
import net.sourceforge.ondex.filter.unconnected.Filter;
import net.sourceforge.ondex.tools.ondex.ONDEXGraphCloner;
import rres.knetminer.datasource.ondexlocal.Hits;
import rres.knetminer.datasource.ondexlocal.PublicationUtils;
import rres.knetminer.datasource.ondexlocal.service.utils.FisherExact;
import rres.knetminer.datasource.ondexlocal.service.utils.GeneHelper;
import rres.knetminer.datasource.ondexlocal.service.utils.QTL;
import uk.ac.ebi.utils.exceptions.ExceptionUtils;
import uk.ac.ebi.utils.io.IOUtils;

import static net.sourceforge.ondex.core.util.ONDEXGraphUtils.getAttrValue;
import static net.sourceforge.ondex.core.util.ONDEXGraphUtils.getAttrValueAsString;
import static net.sourceforge.ondex.core.util.ONDEXGraphUtils.getOrCreateAttributeName;
import static net.sourceforge.ondex.core.util.ONDEXGraphUtils.getOrCreateConceptClass;
import static net.sourceforge.ondex.core.util.ONDEXGraphUtils.getAttributeName;


import static java.lang.Math.sqrt;
import static java.lang.Math.pow;


/**
 * Parent class to all ondex service provider classes implementing organism
 * specific searches.
 *
 * @author Marco Brandizi (refactored heavily in 2020)
 * @author taubertj, pakk, singha
 */
@Component
public class OndexServiceProvider 
{
	@Autowired
	private DataService dataService;

	@Autowired
	private SearchService searchService;
	
	// TODO: continue with migrating stuff into this component
	@Autowired
	private SemanticMotifService semanticMotifService;
	
	@Autowired
	private ExportService exportService;
	
	
	private static AbstractApplicationContext springContext;
	
	private final Logger log = LogManager.getLogger ( getClass() );
	
	/**
	 * It's a singleton, use {@link #getInstance()}.
	 */
  private OndexServiceProvider () {}
  
	
	public DataService getDataService () {
		return dataService;
	}
	
	public SearchService getSearchService ()
	{
		return searchService;
	}

	public SemanticMotifService getSemanticMotifService ()
	{
		return semanticMotifService;
	}

	public ExportService getExportService ()
	{
		return exportService;
	}


	public static OndexServiceProvider getInstance () 
	{
		initSpring ();
		return springContext.getBean ( OndexServiceProvider.class );
	}
  
  private static void initSpring ()
	{
		// Double-check lazy init (https://www.geeksforgeeks.org/java-singleton-design-pattern-practices-examples/)
		if ( springContext != null ) return;
		
		synchronized ( OndexServiceProvider.class )
		{
			if ( springContext != null ) return;
			
			springContext = new AnnotationConfigApplicationContext ( "rres.knetminer.datasource.ondexlocal.service" );
			springContext.registerShutdownHook ();
			
			getInstance ().log.info ( "Spring context for {} initialised", OndexServiceProvider.class.getSimpleName () );
		}		
	}	
	
	
	public void initGraph ( String configXmlPath )
	{
		this.dataService.loadOptions ( configXmlPath );
		dataService.initGraph ();
		
		this.searchService.indexOndexGraph ();
		this.semanticMotifService.initSemanticMotifData ();
		
		this.exportService.exportGraphStats ();
	}
	
	
	
	
	// -------------------------------- OLD STUFF --------------------------------------------
	
	/**
	 * Old code for OSP is temporary DOWN here, waiting to be reviewed.
	 */
	
    /**
    * defaultExportedPublicationCount value
    */
    public static final String OPT_DEFAULT_NUMBER_PUBS = "defaultExportedPublicationCount";
        

    /**
     * Export the Ondex graph as a JSON file using the Ondex JSON Exporter plugin.
     *
     * @return a pair containing the JSON result and the the graph that was actually exported
     * (ie, the one computed by {@link Filter filtering isolated entities}.
     * 
     * Note that this used to return just a string and to set nodeCount and relationshipCount
     * WE MUST STOP PROGRAMMING THIS BAD DAMN IT!!!
     * 
     */
    public Pair<String, ONDEXGraph> exportGraph2Json(ONDEXGraph og) throws InvalidPluginArgumentException
    {
      // Unconnected filter
      Filter uFilter = new Filter();
      ONDEXPluginArguments uFA = new ONDEXPluginArguments(uFilter.getArgumentDefinitions());
      uFA.addOption(ArgumentNames.REMOVE_TAG_ARG, true);

      List<String> ccRestrictionList = Arrays.asList(
      	"Publication", "Phenotype", "Protein",
        "Drug", "Chromosome", "Path", "Comp", "Reaction", "Enzyme", "ProtDomain", "SNP",
        "Disease", "BioProc", "Trait"
      );
      ccRestrictionList.stream().forEach(cc -> 
      {
        try {
        	uFA.addOption(ArgumentNames.CONCEPTCLASS_RESTRICTION_ARG, cc);
        } 
        catch (InvalidPluginArgumentException ex) {
        	// TODO: End user doesn't get this!
        	log.error ( "Failed to restrict concept class " + cc + ": " + ex, ex );
        }
      });
      log.info ( "Filtering concept classes " + ccRestrictionList );

      uFilter.setArguments(uFA);
      uFilter.setONDEXGraph(og);
      uFilter.start();

      ONDEXGraph graph2 = new MemoryONDEXGraph ( "FilteredGraphUnconnected" );
      uFilter.copyResultsToNewGraph ( graph2 );

      // Export the graph as JSON too, using the Ondex JSON Exporter plugin.
      Export jsonExport = new Export();
      File exportFile = null;
      try 
      {
        exportFile = File.createTempFile ( "knetminer", "graph");
        exportFile.deleteOnExit(); // Just in case we don't get round to deleting it ourselves
        String exportPath = exportFile.getAbsolutePath (); 
        
        ONDEXPluginArguments epa = new ONDEXPluginArguments(jsonExport.getArgumentDefinitions());
        epa.setOption(FileArgumentDefinition.EXPORT_FILE, exportPath);

        log.debug ( "JSON Export file: " + epa.getOptions().get(FileArgumentDefinition.EXPORT_FILE) );

        jsonExport.setArguments(epa);
        jsonExport.setONDEXGraph(graph2);
        log.debug ( 
        	"Export JSON data: Total concepts= " + graph2.getConcepts().size() + " , Relations= "
        	+ graph2.getRelations().size()
        );
        // Export the contents of the 'graph' object as multiple JSON
        // objects to an output file.
        jsonExport.start();
        
        log.debug ( "Network JSON file created:" + exportPath );
        
        // TODO: The JSON exporter uses this too, both should become UTF-8
        return Pair.of ( IOUtils.readFile ( exportPath, Charset.defaultCharset() ), graph2 );
      } 
      catch (IOException ex)
      {
      	// TODO: client side doesn't know anything about this, likely wrong
        log.error ( "Failed to export graph", ex );
        return Pair.of ( "", graph2 );
      }
      finally {
      	if ( exportFile != null ) exportFile.delete ();
      }
    }
    
    /**
     * Creates a new keyword for finding the NOT list
     *
     * @param keyword original keyword
     * @return new keyword for searching the NOT list
     *
     * TODO - KHP: There must be a smarter way (regex?) for getting the NOT "terms"
     */
    private String createNotList(String keyword)
    {
      String result = "";
      if (keyword == null) keyword = "";

      keyword = keyword.replace("(", "");
      keyword = keyword.replace(")", "");

      String[] keySplitedOrAnd = keyword.split ( " *(AND|OR) *" );
      for (String keyOA : keySplitedOrAnd)
      {
        String[] keySplitedNOT = keyOA.split ( "NOT" );
        // Initial value is skipped
        for ( int i = 1; i < keySplitedNOT.length; i++ )
        {
        	if ( !result.isEmpty () ) result += " OR ";
          result += keySplitedNOT [ i ];
        }
      }
      return result;
    }

        
    /**
     * Merge two maps using the greater scores. This is needed when a keyword matches more than 
     * one concept field eg. name, description and attribute. It will ensure that the highest
     * Lucene score is used in the Gene Rank.
     *
     * @param hit2score map that holds all hits and scores
     * @param sHits map that holds search results
     */
    private void mergeHits ( 
    	HashMap<ONDEXConcept, Float> hit2score, ScoredHits<ONDEXConcept> sHits, ScoredHits<ONDEXConcept> notHits
    )
		{
    	sHits.getOndexHits ().stream ()
    	.filter ( c -> notHits == null || !notHits.getOndexHits ().contains ( c ) )
    	.map ( c -> c instanceof LuceneConcept ? ( (LuceneConcept) c ).getParent () : c )
    	.forEach ( c -> hit2score.merge ( c, sHits.getScoreOnEntity ( c ), Math::max ) );
		}

        
    /**
     * Search for concepts in Knowledge Graph which contain the keywords
     *
     * @param keyword user-specified keyword
     * @return concepts that match the keyword and their Lucene score
     * @throws IOException
     * @throws ParseException
     */
		public Map<ONDEXConcept, Float> searchLucene ( 
			String keywords, Collection<ONDEXConcept> geneList, boolean includePublications 
		) throws IOException, ParseException
		{
			var graph = dataService.getGraph ();
			var genes2Concepts = semanticMotifService.getGenes2Concepts ();
			Set<AttributeName> atts = graph.getMetaData ().getAttributeNames ();
			
			// TODO: We should search across all accession datasources or make this configurable in settings
			String[] datasources = { "PFAM", "IPRO", "UNIPROTKB", "EMBL", "KEGG", "EC", "GO", "TO", "NLM", "TAIR",
					"ENSEMBLGENE", "PHYTOZOME", "IWGSC", "IBSC", "PGSC", "ENSEMBL" };
			
			// sources identified in KNETviewer
			/*
			 * String[] new_datasources= { "AC", "DOI", "CHEBI", "CHEMBL", "CHEMBLASSAY", "CHEMBLTARGET", "EC", "EMBL",
			 * "ENSEMBL", "GENB", "GENOSCOPE", "GO", "INTACT", "IPRO", "KEGG", "MC", "NC_GE", "NC_NM", "NC_NP", "NLM",
			 * "OMIM", "PDB", "PFAM", "PlnTFDB", "Poplar-JGI", "PoplarCyc", "PRINTS", "PRODOM", "PROSITE", "PUBCHEM",
			 * "PubMed", "REAC", "SCOP", "SOYCYC", "TAIR", "TX", "UNIPROTKB", "UNIPROTKB-COV", "ENSEMBL-HUMAN"};
			 */
			Set<String> dsAcc = new HashSet<> ( Arrays.asList ( datasources ) );

			HashMap<ONDEXConcept, Float> hit2score = new HashMap<> ();

			keywords = StringUtils.trimToEmpty ( keywords );
			
			if ( keywords.isEmpty () && geneList != null && !geneList.isEmpty () )
			{
				log.info ( "No keyword, skipping Lucene stage, using mapGene2Concept instead" );
				for ( ONDEXConcept gene : geneList )
				{
					if ( gene == null ) continue;
					if ( genes2Concepts.get ( gene.getId () ) == null ) continue;
					for ( int conceptId : genes2Concepts.get ( gene.getId () ) )
					{
						ONDEXConcept concept = graph.getConcept ( conceptId );
						if ( includePublications || !concept.getOfType ().getId ().equalsIgnoreCase ( "Publication" ) )
							hit2score.put ( concept, 1.0f );
					}
				}

				return hit2score;
			}

			// TODO: Actually, we should use LuceneEnv.DEFAULTANALYZER, which 
			// consider different field types. See https://stackoverflow.com/questions/62119328 
			Analyzer analyzer = new StandardAnalyzer ();

			// added to overcome double quotes issue
			// if changing this, need to change genepage.jsp and evidencepage.jsp
			keywords = keywords.replace ( "###", "\"" );
			log.debug ( "Keyword is:" + keywords );

			// creates the NOT list (list of all the forbidden documents)
			String notQuery = createNotList ( keywords );
			String crossTypesNotQuery = "";
			ScoredHits<ONDEXConcept> notList = null;
			if ( !"".equals ( notQuery ) )
			{
				crossTypesNotQuery = "ConceptAttribute_AbstractHeader:(" + notQuery + ") OR ConceptAttribute_Abstract:("
					+ notQuery + ") OR Annotation:(" + notQuery + ") OR ConceptName:(" + notQuery + ") OR ConceptID:("
					+ notQuery + ")";
				String fieldNameNQ = getLuceneFieldName ( "ConceptName", null );
				QueryParser parserNQ = new QueryParser ( fieldNameNQ, analyzer );
				Query qNQ = parserNQ.parse ( crossTypesNotQuery );
				//TODO: The top 2000 restriction should be configurable in settings and documented
				notList = searchService.luceneMgr.searchTopConcepts ( qNQ, 2000 );
			}

			// number of top concepts retrieved for each Lucene field
			int maxConcepts = 2000;

			// search concept attributes
			for ( AttributeName att : atts )
				luceneConceptSearchHelper ( 
					keywords, "ConceptAttribute", att.getId (), maxConcepts, hit2score, notList,
					analyzer
				);				

			// Search concept accessions
			for ( String dsAc : dsAcc )
				luceneConceptSearchHelper ( 
					keywords,  "ConceptAccession", dsAc, maxConcepts, hit2score, notList,
					analyzer
				);				
				

			// Search concept names
			luceneConceptSearchHelper ( 
				keywords, "ConceptName", null, maxConcepts, hit2score, notList,
				analyzer
			);				
			
			// search concept description
			luceneConceptSearchHelper ( 
				keywords, "Description", null, maxConcepts, hit2score, notList,
				analyzer
			);				
			
			// search concept annotation
			luceneConceptSearchHelper ( 
				keywords, "Annotation", null, maxConcepts, hit2score, notList,
				analyzer
			);				
			
			log.info ( "searchLucene(), keywords: \"{}\", returning {} total hits", keywords, hit2score.size () );
			return hit2score;
		}

		/**
		 * KnetMiner Gene Rank algorithm
		 * Computes a {@link SemanticMotifsSearchResult} from the result of a gene search.
		 * Described in detail in Hassani-Pak et al. (2020)
		 */
		public SemanticMotifsSearchResult getScoredGenesMap ( Map<ONDEXConcept, Float> hit2score ) 
		{
			Map<ONDEXConcept, Double> scoredCandidates = new HashMap<> ();
			var graph = dataService.getGraph ();
		
			log.info ( "Total hits from lucene: " + hit2score.keySet ().size () );
		
			// 1st step: create map of genes to concepts that contain query terms
			// In other words: Filter the global gene2concept map for concept that contain the keyword
			Map<Integer, Set<Integer>> mapGene2HitConcept = new HashMap<> ();
			
			var concepts2Genes = semanticMotifService.getConcepts2Genes ();
			var genes2PathLengths = semanticMotifService.getGenes2PathLengths ();
			var genesCount = dataService.getGenomeGenesCount ();
			
			hit2score.keySet ()
			.stream ()
			.map ( ONDEXConcept::getId )
			.filter ( concepts2Genes::containsKey )
			.forEach ( conceptId ->
			{
				for ( int geneId: concepts2Genes.get ( conceptId ) )
					mapGene2HitConcept.computeIfAbsent ( geneId, thisGeneId -> new HashSet<> () )
					.add ( conceptId );
			});
			
	
			// 2nd step: calculate a score for each candidate gene
			for ( int geneId : mapGene2HitConcept.keySet () )
			{
				// weighted sum of all evidence concepts
				double weightedEvidenceSum = 0;
	
				// iterate over each evidence concept and compute a weight that is composed of
				// three components
				for ( int cId : mapGene2HitConcept.get ( geneId ) )
				{
					// relevance of search term to concept
					float luceneScore = hit2score.get ( graph.getConcept ( cId ) );
	
					// specificity of evidence to gene
					double igf = Math.log10 ( (double) genesCount / concepts2Genes.get ( cId ).size () );
	
					// inverse distance from gene to evidence
					Integer pathLen = genes2PathLengths.get ( geneId + "//" + cId );
					if ( pathLen == null ) 
						log.info ( "WARNING: Path length is null for: " + geneId + "//" + cId );
					
					double distance = pathLen == null ? 0 : ( 1d / pathLen );
	
					// take the mean of all three components
					double evidenceWeight = ( igf + luceneScore + distance ) / 3;
	
					// sum of all evidence weights
					weightedEvidenceSum += evidenceWeight;
				}
	
				// normalisation method 1: size of the gene knoweldge graph
				// double normFactor = 1 / (double) genes2Concepts.get(geneId).size();
				// normalisation method 2: size of matching evidence concepts only (mean score)
				// double normFactor = 1 / Math.max((double) mapGene2HitConcept.get(geneId).size(), 3.0);
				// No normalisation for now as it's too experimental.
				// This means better studied genes will appear top of the list
				double knetScore = /* normFactor * */ weightedEvidenceSum;
	
				scoredCandidates.put ( graph.getConcept ( geneId ), knetScore );
			}
			
			// Sort by best scores
			Map<ONDEXConcept, Double> sortedCandidates = scoredCandidates.entrySet ().stream ()
			.sorted ( Collections.reverseOrder ( Map.Entry.comparingByValue () ) )
			.collect ( toMap ( Map.Entry::getKey, Map.Entry::getValue, ( e1, e2 ) -> e2, LinkedHashMap::new ) );
			return new SemanticMotifsSearchResult ( mapGene2HitConcept, sortedCandidates );
		}

		
    /**
     * Searches for genes within genomic regions (QTLs), using the special format in the parameter.
     *
     */
		public Set<ONDEXConcept> fetchQTLs ( List<String> qtlsStr )
		{
			log.info ( "searching QTL against: {}", qtlsStr );
			Set<ONDEXConcept> concepts = new HashSet<> ();

			// convert List<String> qtlStr to List<QTL> qtls
			List<QTL> qtls = QTL.fromStringList ( qtlsStr );

			for ( QTL qtl : qtls )
			{
				try
				{
					String chrQTL = qtl.getChromosome ();
					int startQTL = qtl.getStart ();
					int endQTL = qtl.getEnd ();
					log.info ( "user QTL (chr, start, end): " + chrQTL + " , " + startQTL + " , " + endQTL );
					// swap start with stop if start larger than stop
					if ( startQTL > endQTL )
					{
						int tmp = startQTL;
						startQTL = endQTL;
						endQTL = tmp;
					}

					var graph = dataService.getGraph ();
					var gmeta = graph.getMetaData ();
					ConceptClass ccGene = gmeta.getConceptClass ( "Gene" );

					Set<ONDEXConcept> genes = graph.getConceptsOfConceptClass ( ccGene );

					log.info ( "searchQTL, found {} matching gene(s)", genes.size () );

					for ( ONDEXConcept gene : genes )
					{
						GeneHelper geneHelper = new GeneHelper ( graph, gene );

						String geneChr = geneHelper.getChromosome ();
						if ( geneChr == null ) continue;
						if ( !chrQTL.equals ( geneChr )) continue;

						int geneStart = geneHelper.getBeginBP ( true );
						if ( geneStart == 0 ) continue;

						int geneEnd = geneHelper.getEndBP ( true );
						if ( geneEnd == 0 ) continue;

						if ( ! ( geneStart >= startQTL && geneEnd <= endQTL ) ) continue;
						
						if ( !this.dataService.containsTaxId ( geneHelper.getTaxID () ) ) continue;

						concepts.add ( gene );
					}
				}
				catch ( Exception e )
				{
					// TODO: the user doesn't get any of this!
					log.error ( "Not valid qtl: " + e.getMessage (), e );
				}
			}
			return concepts;
		}

		
    /**
     * Searches the knowledge base for QTL concepts that match any of the user
     * input terms.
     * 
     */
    private Set<QTL> getQTLHelpers ( String keyword ) throws ParseException
    {
    	var graph = dataService.getGraph ();
  		var gmeta = graph.getMetaData();
      ConceptClass ccTrait = gmeta.getConceptClass("Trait");
      ConceptClass ccQTL = gmeta.getConceptClass("QTL");
      ConceptClass ccSNP = gmeta.getConceptClass("SNP");

      // no Trait-QTL relations found
      if (ccTrait == null && (ccQTL == null || ccSNP == null)) return new HashSet<>();

      // no keyword provided
      if (keyword == null || keyword.equals ( "" ) ) return new HashSet<>();

      log.debug ( "Looking for QTLs..." );
      
      // If there is not traits but there is QTLs then we return all the QTLs
      if (ccTrait == null) return getAllQTLHelpers ();
      return findQTLForTrait ( keyword );
    }

    
    private Set<QTL> getAllQTLHelpers ()
    {
      log.info ( "No Traits found: all QTLS will be shown..." );

      Set<QTL> results = new HashSet<>();
      
      var graph = dataService.getGraph ();
      var gmeta = graph.getMetaData ();
      ConceptClass ccQTL = gmeta.getConceptClass("QTL");
      
      // results = graph.getConceptsOfConceptClass(ccQTL);
      for (ONDEXConcept qtl : graph.getConceptsOfConceptClass(ccQTL))
      {
        String type = qtl.getOfType().getId();
        String chrName = getAttrValue ( graph, qtl, "Chromosome" );
        int start = (Integer) getAttrValue ( graph, qtl, "BEGIN" );
        int end = (Integer) getAttrValue ( graph, qtl, "END" );
        
        String trait = Optional.ofNullable ( getAttrValueAsString ( graph, qtl, "Trait", false ) )
        	.orElse ( "" );
        
        String taxId = Optional.ofNullable ( getAttrValueAsString ( graph, qtl, "TAXID", false ) )
        	.orElse ( "" );
        
        String label = qtl.getConceptName().getName();
        
        results.add ( new QTL ( chrName, type, start, end, label, "", 1.0f, trait, taxId ) );
      }
      return results;    	
    }

    
    /**
     * Find all QTL and SNP that are linked to Trait concepts that contain a keyword
     * Assumes that KG is modelled as Trait->QTL and Trait->SNP with PVALUE on relations
     * 
     */	
    private Set<QTL> findQTLForTrait ( String keyword ) throws ParseException
    {
  		// TODO: actually LuceneEnv.DEFAULTANALYZER should be used for all fields
  	  // This chooses the appropriate analyzer depending on the field.
  	
      // be careful with the choice of analyzer: ConceptClasses are not
      // indexed in lowercase letters which let the StandardAnalyzer crash
  		//
      Analyzer analyzerSt = new StandardAnalyzer();
      Analyzer analyzerWS = new WhitespaceAnalyzer();

      String fieldCC = getLuceneFieldName ( "ConceptClass", null );
      QueryParser parserCC = new QueryParser ( fieldCC, analyzerWS );
      Query cC = parserCC.parse("Trait");

      String fieldCN = getLuceneFieldName ( "ConceptName", null);
      QueryParser parserCN = new QueryParser(fieldCN, analyzerSt);
      Query cN = parserCN.parse(keyword);

      BooleanQuery finalQuery = new BooleanQuery.Builder()
      	.add ( cC, BooleanClause.Occur.MUST )
        .add ( cN, BooleanClause.Occur.MUST )
        .build();
      
      log.info( "QTL search query: {}", finalQuery.toString() );

      ScoredHits<ONDEXConcept> hits = searchService.luceneMgr.searchTopConcepts ( finalQuery, 100 );
      
      var graph = dataService.getGraph ();
  		var gmeta = graph.getMetaData();
      ConceptClass ccQTL = gmeta.getConceptClass("QTL");
      ConceptClass ccSNP = gmeta.getConceptClass("SNP");
      
      Set<QTL> results = new HashSet<>();
      
      for ( ONDEXConcept c : hits.getOndexHits() ) 
      {
          if (c instanceof LuceneConcept) c = ((LuceneConcept) c).getParent();
          Set<ONDEXRelation> rels = graph.getRelationsOfConcept(c);
          
          for (ONDEXRelation r : rels) 
          {
          	// TODO better variable names: con, fromType and toType
          	var conQTL = r.getFromConcept();
          	var conQTLType = conQTL.getOfType ();
          	var toType = r.getToConcept ().getOfType ();
          	
            // skip if not QTL or SNP concept
            if ( !( conQTLType.equals(ccQTL) || toType.equals(ccQTL)
                 		|| conQTLType.equals(ccSNP) || toType.equals(ccSNP) ) )
            	continue;
              
            // QTL-->Trait or SNP-->Trait
            String chrName = getAttrValueAsString ( graph, conQTL, "Chromosome", false );
            if ( chrName == null ) continue;

            Integer start = (Integer) getAttrValue ( graph, conQTL, "BEGIN", false );
            if ( start == null ) continue;

            Integer end = (Integer) getAttrValue ( graph, conQTL, "END", false );
            if ( end == null ) continue;
            
            String type = conQTLType.getId();
            String label = conQTL.getConceptName().getName();
            String trait = c.getConceptName().getName();
            
            float pValue = Optional.ofNullable ( (Float) getAttrValue ( graph, r, "PVALUE", false ) )
            	.orElse ( 1.0f );

            String taxId = Optional.ofNullable ( getAttrValueAsString ( graph, conQTL, "TAXID", false ) )
              	.orElse ( "" );
            
            
            results.add ( new QTL ( chrName, type, start, end, label, "", pValue, trait, taxId ) );
          } // for concept relations
      } // for getOndexHits
      return results;    	
    }

    
    /**
     * Searches for ONDEXConcepts with the given accessions in the OndexGraph. Assumes a keyword-oriented syntax
     * for the accessions, eg, characters like brackets are removed.
     *
     * @param List<String> accessions
     * @return Set<ONDEXConcept>
     */
		public Set<ONDEXConcept> searchGenesByAccessionKeywords ( List<String> accessions )
		{
			if ( accessions.size () == 0 ) return null;
			
      var graph = dataService.getGraph ();
			AttributeName attTAXID = ONDEXGraphUtils.getAttributeName ( graph, "TAXID" ); 
			ConceptClass ccGene = graph.getMetaData ().getConceptClass ( "Gene" );
			Set<ONDEXConcept> seed = graph.getConceptsOfConceptClass ( ccGene );

			Set<String> normAccs = accessions.stream ()
			.map ( acc -> 
				acc.replaceAll ( "^[\"()]+", "" )
				.replaceAll ( "[\"()]+$", "" )
				.toUpperCase () 
			).collect ( Collectors.toSet () );			
			
			return seed.stream ()
			.filter ( gene -> {
        String thisTaxId = getAttrValueAsString ( gene, attTAXID, false );
        return dataService.containsTaxId ( thisTaxId );
			})
			.filter ( gene ->
			{
				if ( gene.getConceptAccessions ()
				.stream ()
				.map ( ConceptAccession::getAccession )
				.map ( String::toUpperCase )
				.anyMatch ( normAccs::contains ) ) return true;

				// Search the input in names too, it might be there
				if ( gene.getConceptNames ()
				.stream ()
				.map ( ConceptName::getName )
				.map ( String::toUpperCase )
				.anyMatch ( normAccs::contains ) ) return true;
				
				return false;
			})
			.collect ( Collectors.toSet () );
		}

		
    /**
     * Searches genes related to an evidence, fetches the corresponding semantic motifs and merges
     * the paths between them into the resulting graph.
     *
     */
		@SuppressWarnings ( "rawtypes" )
		public ONDEXGraph evidencePath ( Integer evidenceOndexId, Set<ONDEXConcept> genes )
		{
			log.info ( "evidencePath() - evidenceOndexId: {}", evidenceOndexId );
      var graph = dataService.getGraph ();
			var concepts2Genes = semanticMotifService.getConcepts2Genes ();

			// Searches genes related to the evidenceID. If user genes provided, only include those.
			Set<ONDEXConcept> relatedONDEXConcepts = new HashSet<> ();
			for ( Integer rg : concepts2Genes.get ( evidenceOndexId ) )
			{
				ONDEXConcept gene = graph.getConcept ( rg );
				if ( genes == null || genes.isEmpty () || genes.contains ( gene ) )
					relatedONDEXConcepts.add ( gene );
			}

			// the results give us a map of every starting concept to every valid
			// path
			Map<ONDEXConcept, List<EvidencePathNode>> evidencePaths = 
				semanticMotifService.getGraphTraverser ().traverseGraph ( graph, relatedONDEXConcepts, null );

			// create new graph to return
			ONDEXGraph subGraph = new MemoryONDEXGraph ( "evidencePathGraph" );
			ONDEXGraphCloner graphCloner = new ONDEXGraphCloner ( graph, subGraph );
			// TODO: what's for?
			ONDEXGraphRegistry.graphs.put ( subGraph.getSID (), subGraph );
			// Highlights the right path and hides the path that doesn't leads to
			// the evidence
			for ( List<EvidencePathNode> evidencePath : evidencePaths.values () )
			{
				for ( EvidencePathNode pathNode : evidencePath )
				{
					// search last concept of semantic motif for keyword
					int indexLastCon = pathNode.getConceptsInPositionOrder ().size () - 1;
					ONDEXConcept lastCon = (ONDEXConcept) pathNode.getConceptsInPositionOrder ().get ( indexLastCon );
					if ( lastCon.getId () == evidenceOndexId ) highlightPath ( pathNode, graphCloner, false );
					// else hidePath(path,graphCloner);
				}
			}
			ONDEXGraphRegistry.graphs.remove ( subGraph.getSID () );

			return subGraph;
		}


    /**
     * Converts a search string into a list of words
     *
     * @return null or the list of words
     */
		private Set<String> getSearchWords ( String searchString )
		{
			Set<String> result = new HashSet<> ();
			searchString = searchString
			.replace ( "(", " " )
			.replace ( ")", " " )
			.replace ( "AND", " " )
			.replace ( "OR", " " )
			.replace ( "NOT", " " )
			.replaceAll ( "\\s+", " " )
			.trim ();
					
			for (
				// TODO: cache the pattern
				var tokenMatcher = Pattern.compile ( "\"[^\"]+\"|[^\\s]+" ).matcher ( searchString );
				tokenMatcher.find ();
			)
			{
				String token = tokenMatcher.group ();
				// Also fixes errors like odd no. of quotes
				if ( token.startsWith ( "\"") ) token = token.substring ( 1 );
				if ( token.endsWith ( "\"" ) ) token = token.substring ( 0, token.length () - 1 );
				token = token.trim ();

				result.add ( token );
			}

			log.info ( "getSearchWords(), tokens: {}", result );
			return result;
		}



    /**
     * Generates a subgraph for a set of genes and graph queries. The subgraph is annotated by setting node,ege visibility and size attributes.
     * Annotation is based on either paths to keyword concepts (if provided) or a set of rules based on paths to Trait/Phenotype concepts.
     *
     * @param seed List of selected genes
     * @param keyword
     * @return subGraph
     */
		public ONDEXGraph findSemanticMotifs ( Set<ONDEXConcept> seed, String keyword )
		{
			log.info ( "findSemanticMotifs(), keyword: {}", keyword );
			// Searches with Lucene: luceneResults
			Map<ONDEXConcept, Float> luceneResults = null;
			try
			{
				luceneResults = searchLucene ( keyword, seed, false );
			}
			catch ( Exception e )
			{
				// TODO: does it make sense to continue?!
				// KHP: Does it go here when the keyword is null?
				log.error ( "Lucene search failed", e );
				luceneResults = Collections.emptyMap ();
			}

			var graph = dataService.getGraph ();
			var options = dataService.getOptions ();
			
			// the results give us a map of every starting concept to every valid path
			Map<ONDEXConcept, List<EvidencePathNode>> results = semanticMotifService.getGraphTraverser ()
				.traverseGraph ( graph, seed, null );

			Set<ONDEXConcept> keywordConcepts = new HashSet<> ();
			Set<EvidencePathNode> pathSet = new HashSet<> ();

			// added to overcome double quotes issue
			// if changing this, need to change genepage.jsp and evidencepage.jsp
			keyword = keyword.replace ( "###", "\"" );

			Set<String> keywords = "".equals ( keyword ) 
				? Collections.emptySet ()
				: this.getSearchWords ( keyword );
					
			Map<String, String> keywordColourMap = createHilightColorMap ( keywords );
					
			// create new graph to return
			final ONDEXGraph subGraph = new MemoryONDEXGraph ( "SemanticMotifGraph" );
			ONDEXGraphCloner graphCloner = new ONDEXGraphCloner ( graph, subGraph );

			ONDEXGraphRegistry.graphs.put ( subGraph.getSID (), subGraph );

			Set<ONDEXConcept> pubKeywordSet = new HashSet<> ();

			for ( List<EvidencePathNode> paths : results.values () )
			{
				for ( EvidencePathNode path : paths )
				{
					// add all semantic motifs to the new graph
					( (Set<ONDEXConcept>) path.getAllConcepts () )
						.forEach ( graphCloner::cloneConcept );
					
					( (Set<ONDEXRelation>) path.getAllRelations () )
						.forEach ( graphCloner::cloneRelation );

					// search last concept of semantic motif for keyword
					int indexLastCon = path.getConceptsInPositionOrder ().size () - 1;
					ONDEXConcept endNode = (ONDEXConcept) path.getConceptsInPositionOrder ().get ( indexLastCon );

					// no-keyword, set path to visible if end-node is Trait or Phenotype
					if ( keyword == null || keyword.isEmpty() )
					{
						highlightPath ( path, graphCloner, true );
						continue;
					}

					// keyword-mode and end concept contains keyword, set path to visible
					if ( !luceneResults.containsKey ( endNode ) ) {
            // collect all paths that did not qualify
						pathSet.add ( path );
						continue;
					}
					
					// keyword-mode -> do text and path highlighting
					ONDEXConcept cloneCon = graphCloner.cloneConcept ( endNode );

					// highlight keyword in any concept attribute
					if ( !keywordConcepts.contains ( cloneCon ) )
					{
						this.highlightSearchKeywords ( cloneCon, keywordColourMap );
						keywordConcepts.add ( cloneCon );
	
						if ( endNode.getOfType ().getId ().equalsIgnoreCase ( "Publication" ) )
							pubKeywordSet.add ( cloneCon );
					}

					// set only paths from gene to evidence nodes to visible
					highlightPath ( path, graphCloner, false );
				} // for path
			} // for paths

			// special case when none of nodes contains keyword (no-keyword-match)
			// set path to visible if end-node is Trait or Phenotype
			if ( keywordConcepts.isEmpty () && ! ( keyword == null || keyword.isEmpty () ) )
				for ( EvidencePathNode path : pathSet )
					highlightPath ( path, graphCloner, true );

			ConceptClass ccPub = subGraph.getMetaData ().getConceptClass ( "Publication" );
			Set<Integer> allPubIds = new HashSet<Integer> ();

			// if subgraph has publications do smart filtering of most interesting papers
			if ( ccPub != null )
			{
				// get all publications in subgraph that have and don't have keyword
				Set<ONDEXConcept> allPubs = subGraph.getConceptsOfConceptClass ( ccPub );

				allPubs.stream ()
				.map ( ONDEXConcept::getId )
				.forEach ( allPubIds::add );
				
				AttributeName attYear = subGraph.getMetaData ().getAttributeName ( "YEAR" );

				// if publications with keyword exist, keep most recent papers from pub-keyword set
				// else, just keep most recent papers from total set
				Set<ONDEXConcept> selectedPubs = pubKeywordSet.isEmpty () ? allPubs : pubKeywordSet;
				List<Integer> newPubIds = PublicationUtils.newPubsByNumber ( 
					selectedPubs, attYear,
					options.getInt ( OPT_DEFAULT_NUMBER_PUBS, -1 )
				);

				// publications that we want to remove
				allPubIds.removeAll ( newPubIds );

				// Keep most recent publications that contain keyword and remove rest from subGraph
				allPubIds.forEach ( subGraph::deleteConcept );
			}

			ONDEXGraphRegistry.graphs.remove ( subGraph.getSID () );

			log.debug ( "Number of seed genes: " + seed.size () );
			log.debug ( "Number of removed publications " + allPubIds.size () );

			return subGraph;
		}
		
		
    
		/**
		 * Creates a mapping between keywords and random HTML colour codes, used by the search highlighting functions.
		 * if colors is null, uses {@link #createHighlightColors(int)}.
		 * If colours are not enough for the set of parameter keywords, they're reused cyclically.
		 */
		private Map<String, String> createHilightColorMap ( Set<String> keywords, List<String> colors )
		{
			if ( colors == null ) colors = createHighlightColors ( keywords.size () );
			Map<String, String> keywordColorMap = new HashMap<> ();
			
			int colIdx = 0;
			for ( String key: keywords )
				keywordColorMap.put ( key, colors.get ( colIdx++ % colors.size () ) );
			
			return keywordColorMap;
		}

		/**
		 * Defaults to null.
		 */
		private Map<String, String> createHilightColorMap ( Set<String> keywords )
		{
			return createHilightColorMap ( keywords, null );
		}

		/**
		 * Can be used with {@link #createHilightColorMap(Set, List)}. Indeed, this is 
		 * what it's used when no color list is sent to it. It genereates a list of the size
		 * sent and made of random different colors with visibility characteristics.
		 * 
		 */
		private List<String> createHighlightColors ( int size )
		{
			Random random = new Random ();
			Set<Integer> colors = new HashSet<> (); // Compare each colour to ensure we never have duplicates
			int colorCode = -1;

			for ( int i = 0; i < size; i++ ) 
			{
				// Ensure colour luminance is >40 (git issue #466),
				// no colours are repeated and are never yellow
				//
				while ( true )
				{
					colorCode = random.nextInt ( 0x666666 + 1 ) + 0x999999; // lighter colours only
					if ( colors.contains ( colorCode ) ) continue;
										
					String colorHex = "#" + Integer.toHexString ( colorCode );
					
					Color colorVal = Color.decode ( colorHex );
					if ( Color.YELLOW.equals ( colorVal ) ) continue;
					
					int colorBrightness = (int) sqrt ( 
						pow ( colorVal.getRed (), 2 ) * .241
						+ pow ( colorVal.getGreen (), 2 ) * .691 
						+ pow ( colorVal.getBlue (), 2 ) * .068 
					);
					
					if ( colorBrightness <= 40 ) continue;
					
					break;
				}
				colors.add ( colorCode ); // Add to colour ArrayList to track colours
			}
			
			return colors.stream ()
			.map ( colCode -> String.format ( "#%06x", colCode ) )
			.collect ( Collectors.toList () );
		}
		
		
		
    /**
     * Helper for {@link #highlightSearchKeywords(ONDEXConcept, Map)}. If the pattern matches the path, it  
     * {@link Matcher#replaceAll(String) replaces} the matching bits of the target with the new
     * highligher string and passes the result to the consumer (for operations like assignments)
     * 
     * Please note:
     * 
     * - target is assumed to be a Lucene token, "xxx*" or "xxx?" are translated into "\S*" or "\S?", in order to 
     * match the RE semantics.
     * - highlighter is a string for {@link Matcher#replaceAll(String)}, which should use "$1" to match a proper
     * bracket expression in target
     * - the matching is usually case-insensitive, but that depends on how you defined the pattern. 
     */
    private boolean highlightSearchStringFragment ( Pattern pattern, String target, String highlighter, Consumer<String> consumer )
    {
    	Matcher matcher = pattern.matcher ( target );
    	if ( !matcher.find ( 0 ) ) return false;
    	var highlightedStr = matcher.replaceAll ( highlighter );
    	if ( consumer != null ) consumer.accept ( highlightedStr );
    	return true;
    }
    
    /**
     * Helper for {@link #highlightSearchKeywords(ONDEXConcept, Map)}, manages the hightlighting of a single
     * search keyword.
     * 
     */
    private boolean highlightSearchKeyword ( ONDEXConcept concept, String keyword, String highlighter )
    {
			boolean found = false;

			String keywordRe = '(' + keyword + ')';
			// TODO: the end user is supposed to be writing Lucene expressions, 
			// so we fix them this way. But using Lucene for highlighting should be simpler.
			keywordRe = keywordRe.replaceAll ( "\\*", "\\S*" )
				.replaceAll ( "\\?", "\\S?" );
			
			Pattern kwpattern = Pattern.compile ( keywordRe, Pattern.CASE_INSENSITIVE );

			found |= this.highlightSearchStringFragment ( kwpattern, concept.getAnnotation (), highlighter, concept::setAnnotation );
			found |= this.highlightSearchStringFragment ( kwpattern, concept.getDescription (), highlighter, concept::setDescription );
			
			// old name -> is preferred, new name
			HashMap<String, Pair<Boolean, String>> namesToCreate = new HashMap<> ();
			for ( ConceptName cname : concept.getConceptNames () )
			{
				String cnameStr = cname.getName ();
				// TODO: initially cnameStr.contains ( "</span>" ) was skipped too, probably to be removed
				if ( cnameStr == null ) continue;
					
				found |= this.highlightSearchStringFragment ( 
					kwpattern, cnameStr, highlighter, 
					newName -> namesToCreate.put ( cnameStr, Pair.of ( cname.isPreferred (), newName ) ) 
				);
			}
			
			// And now do the replacements for real
			namesToCreate.forEach ( ( oldName, newPair ) -> {
				concept.deleteConceptName ( oldName );
				concept.createConceptName ( newPair.getRight (), newPair.getLeft () );
			});
			

			// search in concept attributes
			for ( Attribute attribute : concept.getAttributes () )
			{
				String attrId = attribute.getOfType ().getId ();
				
				if ( attrId.equals ( "AA" ) || attrId.equals ( "NA" ) 
						 || attrId.startsWith ( "AA_" ) || attrId.startsWith ( "NA_" ) )
					continue;
				
				String value = attribute.getValue ().toString ();
				found |= this.highlightSearchStringFragment ( kwpattern, value, highlighter, attribute::setValue );
			}
			
			return found;
    }
    
    
    /**
     * Searches different fields of a concept for a query or pattern and
     * highlights them.
     * 
     * TODO: this is ugly, Lucene should already have methods to do the same.
     *
     * @return true if one of the concept fields contains the query
     */
		private boolean highlightSearchKeywords ( ONDEXConcept concept, Map<String, String> keywordColourMap )
		{
			// Order the keywords by length to prevent interference by shorter matches that are substrings of longer ones.
			String[] orderedKeywords = keywordColourMap.keySet ().toArray ( new String[ 0 ] );
			
			Comparator<String> strLenComp = (a, b) -> a.length () == b.length () 
				? a.compareTo ( b ) 
				: Integer.compare ( a.length(), b.length() );

			Arrays.sort ( orderedKeywords, strLenComp );
			boolean found = false;

			for ( String key : orderedKeywords )
			{
				var highlighter = "<span style=\"background-color:" + keywordColourMap.get ( key ) + "\">"
						+ "<b>$1</b></span>";				
				found |= highlightSearchKeyword ( concept, key, highlighter );
			}

			return found;
		}
		

    /**
     * Annotate first and last concept and relations of a given path Do
     * annotations on a new graph and not on the original graph
     *
     * @param path Contains concepts and relations of a semantic motif
     * @param graphCloner cloner for the new graph
     * @param doFilter If true only a path to Trait and Phenotype nodes will be
     * made visible
     */
		@SuppressWarnings ( "rawtypes" )
		public void highlightPath ( EvidencePathNode path, ONDEXGraphCloner graphCloner, boolean doFilter )
		{
      var graph = dataService.getGraph ();
			ONDEXGraph gclone = graphCloner.getNewGraph ();
			
			ONDEXGraphMetaData gcloneMeta = gclone.getMetaData ();
			MetaDataFactory gcloneMetaFact = gcloneMeta.getFactory ();
			EntityFactory gcloneFact = gclone.getFactory ();
			AttributeName attSize = getOrCreateAttributeName ( gclone, "size", Integer.class ); 
			AttributeName attVisible = getOrCreateAttributeName ( gclone, "visible", Boolean.class ); 
			AttributeName attFlagged = getOrCreateAttributeName ( gclone, "flagged", Boolean.class ); 
			
			ConceptClass ccTrait = getOrCreateConceptClass ( gclone, "Trait" );
			ConceptClass ccPhenotype = getOrCreateConceptClass ( gclone, "Phenotype" );

			Set<ConceptClass> ccFilter = new HashSet<> ();
			ccFilter.add ( ccTrait );
			ccFilter.add ( ccPhenotype );

			// gene and evidence nodes of path in knowledge graph
			int indexLastCon = path.getConceptsInPositionOrder ().size () - 1;
			ONDEXConcept geneNode = (ONDEXConcept) path.getStartingEntity ();
			ONDEXConcept endNode = (ONDEXConcept) path.getConceptsInPositionOrder ().get ( indexLastCon );

			// get equivalent gene and evidence nodes in new sub-graph
			ONDEXConcept endNodeClone = graphCloner.cloneConcept ( endNode );
			ONDEXConcept geneNodeClone = graphCloner.cloneConcept ( geneNode );

			// all nodes and relations of given path
			Set<ONDEXConcept> cons = path.getAllConcepts ();
			Set<ONDEXRelation> rels = path.getAllRelations ();

			// seed gene should always be visible, flagged and bigger
			if ( geneNodeClone.getAttribute ( attFlagged ) == null )
			{
				geneNodeClone.createAttribute ( attFlagged, true, false );
				geneNodeClone.createAttribute ( attVisible, true, false );
				geneNodeClone.createAttribute ( attSize, 80, false );
			}

			// set all concepts to visible if filtering is turned off
			// OR filter is turned on and end node is of specific type
			if ( !doFilter || ccFilter.contains ( endNodeClone.getOfType () ) )
			{
				for ( ONDEXConcept c : cons )
				{
					ONDEXConcept concept = graphCloner.cloneConcept ( c );
					if ( concept.getAttribute ( attVisible ) != null ) continue;
					concept.createAttribute ( attSize, 50, false );
					concept.createAttribute ( attVisible, true, false );
				}

				// set all relations to visible if filtering is turned off
				// OR filter is turned on and end node is of specific type
				for ( ONDEXRelation rel : rels )
				{
					ONDEXRelation r = graphCloner.cloneRelation ( rel );
					if ( r.getAttribute ( attVisible ) == null )
					{
						// initial size
						r.createAttribute ( attSize, 5, false );
						r.createAttribute ( attVisible, true, false );
					}
				}
			} // if doFilter

			// add gene-QTL-Trait relations to the network
			var genes2QTLs = semanticMotifService.getGenes2QTLs ();
			if ( !genes2QTLs.containsKey ( geneNode.getId () ) ) return;
			
			RelationType rt = gcloneMetaFact.createRelationType ( "is_p" );
			EvidenceType et = gcloneMetaFact.createEvidenceType ( "KnetMiner" );

			Set<Integer> qtlSet = genes2QTLs.get ( geneNode.getId () );
			for ( Integer qtlId : qtlSet )
			{
				ONDEXConcept qtl = graphCloner.cloneConcept ( graph.getConcept ( qtlId ) );
				if ( gclone.getRelation ( geneNodeClone, qtl, rt ) == null )
				{
					ONDEXRelation r = gcloneFact.createRelation ( geneNodeClone, qtl, rt, et );
					r.createAttribute ( attSize, 2, false );
					r.createAttribute ( attVisible, true, false );
				}
				if ( qtl.getAttribute ( attSize ) == null )
				{
					qtl.createAttribute ( attSize, 70, false );
					qtl.createAttribute ( attVisible, true, false );
				}
				
				Set<ONDEXRelation> relSet = graph.getRelationsOfConcept ( graph.getConcept ( qtlId ) );
				for ( ONDEXRelation r : relSet )
				{
					if ( !r.getOfType ().getId ().equals ( "has_mapped" ) ) continue;
					
					ONDEXRelation rel = graphCloner.cloneRelation ( r );
					if ( rel.getAttribute ( attSize ) == null )
					{
						rel.createAttribute ( attSize, 2, false );
						rel.createAttribute ( attVisible, true, false );
					}

					ONDEXConcept tC = r.getToConcept ();
					ONDEXConcept traitCon = graphCloner.cloneConcept ( tC );
					if ( traitCon.getAttribute ( attSize ) != null ) continue;
					{
						traitCon.createAttribute ( attSize, 70, false );
						traitCon.createAttribute ( attVisible, true, false );
					}
				} // for relSet
			} // for qtlSet
		} // highlightPath ()

		
    /**
     * hides the path between a gene and a concept
     *
     * @param path Contains concepts and relations of a semantic motif
     * @param graphCloner cloner for the new graph
     */
		public void hidePath ( EvidencePathNode path, ONDEXGraphCloner graphCloner )
		{
			ONDEXGraph gclone = graphCloner.getNewGraph ();
			AttributeName attVisible = getOrCreateAttributeName ( gclone, "visible", Boolean.class );

			// hide every concept except by the last one
			int indexLastCon = path.getConceptsInPositionOrder ().size () - 1;
			ONDEXConcept lastCon = (ONDEXConcept) path.getConceptsInPositionOrder ().get ( indexLastCon );
			Set<ONDEXConcept> cons = path.getAllConcepts ();
			cons.stream ()
			.filter ( pconcept -> pconcept.getId () == lastCon.getId () )
			.forEach ( pconcept -> {
				ONDEXConcept concept = graphCloner.cloneConcept ( pconcept );
				concept.createAttribute ( attVisible, false, false );
			});
		}

		
    /**
     * Write Genomaps XML file (to a string).
     * 
     * TODO: how is it that a URI has to be used to invoke functions that sit around here, in the same .WAR?!
     * This is bad design, we want a functional layer that could be invoked independently on the higher HTTP 
     * layers, possibly open a ticket to clean this in the medium/long term.
     * 
     * @param apiUrl ws url for API
     * @param genes list of genes to be displayed (all genes for search result)
     * @param userGenes gene list from user
     * @param userQtlStr user QTLs
     * @param keyword user-specified keyword
     * @param maxGenes
     * @param hits search Hits
     * @param listMode
     * @return
     */
		public String writeAnnotationXML ( String apiUrl, List<ONDEXConcept> genes, Set<ONDEXConcept> userGenes,
			List<String> userQtlStr, String keyword, int maxGenes, Hits hits, String listMode,
			Map<ONDEXConcept, Double> scoredCandidates )
		{
			log.info ( "Genomaps: generating XML..." );

			List<QTL> userQtl = QTL.fromStringList ( userQtlStr );  

			// TODO: can we remove this?
			// If user provided a gene list, use that instead of the all Genes (04/07/2018, singha)
			/*
			 * if(userGenes != null) { // use this (Set<ONDEXConcept> userGenes) in place of the genes
			 * ArrayList<ONDEXConcept> genes. genes= new ArrayList<ONDEXConcept> (userGenes);
			 * log.info("Genomaps: Using user-provided gene list... genes: "+ genes.size()); }
			 */
			// added user gene list restriction above (04/07/2018, singha)

      var graph = dataService.getGraph ();
			ONDEXGraphMetaData gmeta = graph.getMetaData ();

			ConceptClass ccQTL = gmeta.getConceptClass ( "QTL" );
			
			Set<QTL> qtlDB = new HashSet<> ();
			if ( ccQTL != null && ! ( keyword == null || "".equals ( keyword ) ) )
			{
				// qtlDB = graph.getConceptsOfConceptClass(ccQTL);
				try
				{
					qtlDB = getQTLHelpers ( keyword );
				}
				catch ( ParseException e )
				{
					// TODO: is it fine to continue without any exception!?
					log.error ( "Failed to find QTLs", e );
				}
			}

			StringBuffer sb = new StringBuffer ();
			sb.append ( "<?xml version=\"1.0\" standalone=\"yes\"?>\n" );
			sb.append ( "<genome>\n" );
			int id = 0;

			// genes are grouped in three portions based on size
			int size = Math.min ( genes.size (), maxGenes );

			log.info ( "visualize genes: " + genes.size () );
			for ( ONDEXConcept c : genes )
			{
				var geneHelper = new GeneHelper ( graph, c );
				
				// only genes that are on chromosomes (not scaffolds)
				// can be displayed in Map View
				String chr = geneHelper.getChromosome ();
				if ( chr == null || "U".equals ( chr ) )
					continue;
				
				int beg = geneHelper.getBeginBP ( true );
				int end = geneHelper.getEndBP ( true );


				String name = c.getPID ();
				// TODO: What does this mean?! Getting a random accession?! Why
				// not using the methods for the shortest name/accession?
				for ( ConceptAccession acc : c.getConceptAccessions () )
					name = acc.getAccession ();

				String label = getMolBioDefaultLabel ( c );
				String query = null;
				try
				{
					query = "keyword=" + URLEncoder.encode ( keyword, "UTF-8" ) + "&amp;list="
							+ URLEncoder.encode ( name, "UTF-8" );
				}
				catch ( UnsupportedEncodingException e )
				{
					log.error ( "Internal error while exporting geno-maps, encoding UTF-8 unsupported(?!)", e );
					throw ExceptionUtils.buildEx ( RuntimeException.class, e,
							"Internal error while exporting geno-maps, encoding UTF-8 unsupported(?!)" );
				}
				String uri = apiUrl + "/network?" + query; // KnetMaps (network) query
				// log.info("Genomaps: add KnetMaps (network) query: "+ uri);

				// Genes
				sb.append ( "<feature id=\"" + id + "\">\n" );
				sb.append ( "<chromosome>" + chr + "</chromosome>\n" );
				sb.append ( "<start>" + beg + "</start>\n" );
				sb.append ( "<end>" + end + "</end>\n" );
				sb.append ( "<type>gene</type>\n" );
				
				if ( id <= size / 3 )
					sb.append ( "<color>0x00FF00</color>\n" ); // Green
				else if ( id > size / 3 && id <= 2 * size / 3 )
					sb.append ( "<color>0xFFA500</color>\n" ); // Orange
				else
					sb.append ( "<color>0xFF0000</color>\n" ); // Red
				
				sb.append ( "<label>" + label + "</label>\n" );
				sb.append ( "<link>" + uri + "</link>\n" );
				
				// Add 'score' tag as well.
				Double score = 0.0;
				if ( scoredCandidates != null && scoredCandidates.get ( c ) != null )
					score = scoredCandidates.get ( c ); // fetch score
				
				sb.append ( "<score>" + score + "</score>\n" ); // score
				sb.append ( "</feature>\n" );

				if ( id++ > maxGenes )
					break;
			}

			log.info ( "Display user QTLs... QTLs provided: " + userQtl.size () );
			for ( QTL region : userQtl )
			{
				String chr = region.getChromosome ();
				int start = region.getStart ();
				int end = region.getEnd ();
				
				String label = Optional.ofNullable ( region.getLabel () )
					.filter ( lbl -> !lbl.isEmpty () )
					.orElse ( "QTL" );

				String query = null;
				try
				{
					query = "keyword=" + URLEncoder.encode ( keyword, "UTF-8" ) + "&amp;qtl=" + URLEncoder.encode ( chr, "UTF-8" )
							+ ":" + start + ":" + end;
				}
				catch ( UnsupportedEncodingException e )
				{
					log.error ( "Internal error while exporting geno-maps, encoding UTF-8 unsupported(?!)", e );
					throw ExceptionUtils.buildEx ( RuntimeException.class, e,
							"Internal error while exporting geno-maps, encoding UTF-8 unsupported(?!)" );
				}
				String uri = apiUrl + "/network?" + query;

				sb.append ( "<feature>\n" );
				sb.append ( "<chromosome>" + chr + "</chromosome>\n" );
				sb.append ( "<start>" + start + "</start>\n" );
				sb.append ( "<end>" + end + "</end>\n" );
				sb.append ( "<type>qtl</type>\n" );
				sb.append ( "<color>0xFF0000</color>\n" ); // Orange
				sb.append ( "<label>" + label + "</label>\n" );
				sb.append ( "<link>" + uri + "</link>\n" );
				sb.append ( "</feature>\n" );
			}

			// TODO: createHilightColorMap() generates colours randomly by default, why doing the same differently, here?!
			// TODO: possibly, move this to a constant

			List<String> colorHex = List.of ( "0xFFB300", "0x803E75", "0xFF6800", "0xA6BDD7", "0xC10020", "0xCEA262", "0x817066",
					"0x0000FF", "0x00FF00", "0x00FFFF", "0xFF0000", "0xFF00FF", "0xFFFF00", "0xDBDB00", "0x00A854", "0xC20061",
					"0xFF7E3D", "0x008F8F", "0xFF00AA", "0xFFFFAA", "0xD4A8FF", "0xA8D4FF", "0xFFAAAA", "0xAA0000", "0xAA00FF",
					"0xAA00AA", "0xAAFF00", "0xAAFFFF", "0xAAFFAA", "0xAAAA00", "0xAAAAFF", "0xAAAAAA", "0x000055", "0x00FF55",
					"0x00AA55", "0x005500", "0x0055FF" );
			// 0xFFB300, # Vivid Yellow
			// 0x803E75, # Strong Purple
			// 0xFF6800, # Vivid Orange
			// 0xA6BDD7, # Very Light Blue
			// 0xC10020, # Vivid Red
			// 0xCEA262, # Grayish Yellow
			// 0x817066, # Medium Gray
			
			Set<String> traits = qtlDB.stream ()
			.map ( QTL::getTrait )
			.collect ( Collectors.toSet () );
			
			Map<String, String> trait2color = createHilightColorMap ( traits, colorHex );

			final var taxIds = this.dataService.getTaxIds ();
			log.info ( "Display QTLs and SNPs... QTLs found: " + qtlDB.size () );
			log.info ( "TaxID(s): {}", taxIds );
			
			for ( QTL loci : qtlDB )
			{
				String type = loci.getType ().trim ();
				String chrQTL = loci.getChromosome ();
				Integer startQTL = loci.getStart ();
				Integer endQTL = loci.getEnd ();
				String label = loci.getLabel ().replaceAll ( "\"", "" );
				String trait = loci.getTrait ();

				Float pvalue = loci.getpValue ();
				String color = trait2color.get ( trait );

				// TODO get p-value of SNP-Trait relations
				if ( type.equals ( "QTL" ) )
				{
					sb.append ( "<feature>\n" );
					sb.append ( "<chromosome>" + chrQTL + "</chromosome>\n" );
					sb.append ( "<start>" + startQTL + "</start>\n" );
					sb.append ( "<end>" + endQTL + "</end>\n" );
					sb.append ( "<type>qtl</type>\n" );
					sb.append ( "<color>" + color + "</color>\n" );
					sb.append ( "<trait>" + trait + "</trait>\n" );
					sb.append ( "<link>http://archive.gramene.org/db/qtl/qtl_display?qtl_accession_id=" + label + "</link>\n" );
					sb.append ( "<label>" + label + "</label>\n" );
					sb.append ( "</feature>\n" );
					// log.info("add QTL: trait, label: "+ trait +", "+ label);
				} 
				else if ( type.equals ( "SNP" ) )
				{
					/* add check if species TaxID (list from client/utils-config.js) contains this SNP's TaxID. */
					if ( this.dataService.containsTaxId ( loci.getTaxID () ) )
					{
						sb.append ( "<feature>\n" );
						sb.append ( "<chromosome>" + chrQTL + "</chromosome>\n" );
						sb.append ( "<start>" + startQTL + "</start>\n" );
						sb.append ( "<end>" + endQTL + "</end>\n" );
						sb.append ( "<type>snp</type>\n" );
						sb.append ( "<color>" + color + "</color>\n" );
						sb.append ( "<trait>" + trait + "</trait>\n" );
						sb.append ( "<pvalue>" + pvalue + "</pvalue>\n" );
						sb.append (
								"<link>http://plants.ensembl.org/arabidopsis_thaliana/Variation/Summary?v=" + label + "</link>\n" );
						sb.append ( "<label>" + label + "</label>\n" );
						sb.append ( "</feature>\n" );
					}
				}

			} // for loci

			sb.append ( "</genome>\n" );
			return sb.toString ();
		} // writeAnnotationXML()

		
		
    /**
     * This table contains all possible candidate genes for given query
     * TODO: too big! Split into separated functions.
     *
     */
		public String writeGeneTable ( 
			List<ONDEXConcept> candidates, Set<ONDEXConcept> userGenes, List<String> qtlsStr, 
			String listMode,  SemanticMotifsSearchResult searchResult 
		)
		{
			log.info ( "generate Gene table..." );
			
			List<QTL> qtls =  QTL.fromStringList ( qtlsStr );
			Set<Integer> userGeneIds = new HashSet<> ();
      var graph = dataService.getGraph ();
			var genes2QTLs = semanticMotifService.getGenes2QTLs ();
			var options = dataService.getOptions ();

			if ( userGenes != null )
			{
				userGeneIds = userGenes.stream ()
					.map ( ONDEXConcept::getId )
					.collect ( Collectors.toSet () );
			} 
			else
				log.info ( "No user gene list defined." );

			if ( qtls.isEmpty () ) log.info ( "No QTL regions defined." );
			
			var mapGene2HitConcept = searchResult.getGeneId2RelatedConceptIds ();
			
			// TODO: but could it be null?!
			var scoredCandidates = Optional.ofNullable ( searchResult.getRelatedConcept2Score () )
				.orElse ( Collections.emptyMap () );			
			
			// Removed ccSNP from Geneview table (12/09/2017)
			// AttributeName attSnpCons = md.getAttributeName("Transcript_Consequence");
			// ConceptClass ccSNP = md.getConceptClass("SNP");

			StringBuffer out = new StringBuffer ();
			// out.append("ONDEX-ID\tACCESSION\tGENE
			// NAME\tCHRO\tSTART\tTAXID\tSCORE\tUSER\tQTL\tEVIDENCE\tEVIDENCES_LINKED\tEVIDENCES_IDs\n");
			out.append ( "ONDEX-ID\tACCESSION\tGENE NAME\tCHRO\tSTART\tTAXID\tSCORE\tUSER\tQTL\tEVIDENCE\n" );
			for ( ONDEXConcept gene : candidates )
			{
				int id = gene.getId ();

				var geneHelper = new GeneHelper ( graph, gene );
				Double score = scoredCandidates.getOrDefault ( gene, 0d );

				// use shortest preferred concept name
				String geneName = getShortestPreferedName ( gene.getConceptNames () );

				boolean isInList = userGenes != null && userGeneIds.contains ( gene.getId () );
 
				List<String> infoQTL = new LinkedList<> ();
				for ( Integer cid : genes2QTLs.getOrDefault ( gene.getId (), Collections.emptySet () ) )
				{
					ONDEXConcept qtl = graph.getConcept ( cid );

					/*
					 * TODO: a TEMPORARY fix for a bug wr're seeing, we MUST apply a similar massage to ALL cases like this,
					 * and hence we MUST move this code to some utility.
					 */
					if ( qtl == null )
					{
						log.error ( "writeTable(): no gene found for id: ", cid );
						continue;
					}
					String acc = Optional.ofNullable ( qtl.getConceptName () )
						.map ( ConceptName::getName )
						.map ( StringEscapeUtils::escapeCsv )
						.orElseGet ( () -> {
							log.error ( "writeTable(): gene name not found for id: {}", cid );
							return "";
						});

					String traitDesc = Optional.of ( getAttrValueAsString ( graph, gene, "Trait", false ) )
						.orElse ( acc );

					// TODO: traitDesc twice?! Looks wrong.
					infoQTL.add ( traitDesc + "//" + traitDesc ); 
				} // for genes2QTLs


				qtls.stream ()
				.filter ( loci -> !loci.getChromosome ().isEmpty () )
				.filter ( loci -> geneHelper.getBeginBP ( true ) >= loci.getStart () )
				.filter ( loci -> geneHelper.getEndBP ( true ) <= loci.getEnd () )
				.map ( loci -> loci.getLabel () + "//" + loci.getTrait () )
				.forEach ( infoQTL::add );

				String infoQTLStr = infoQTL.stream ().collect ( Collectors.joining ( "||" ) );
				
				// get lucene hits per gene
				Set<Integer> luceneHits = mapGene2HitConcept.getOrDefault ( id, Collections.emptySet () );

				// organise by concept class
				Map<String, String> cc2name = new HashMap<> ();

				Set<Integer> evidencesIDs = new HashSet<> ();
				for ( int hitID : luceneHits )
				{
					ONDEXConcept c = graph.getConcept ( hitID );
					evidencesIDs.add ( c.getId () ); // retain all evidences' ID's
					String ccId = c.getOfType ().getId ();

					// skip publications as handled differently (see below)
					if ( ccId.equals ( "Publication" ) ) continue;

					String name = getMolBioDefaultLabel ( c );
					cc2name.merge ( ccId, name, (thisId, oldName) -> oldName + "//" + name );
				}

				// special case for publications to sort and filter most recent publications
				Set<ONDEXConcept> allPubs = luceneHits.stream ()
					.map ( graph::getConcept )
					.filter ( c -> "Publication".equals ( c.getOfType ().getId () ) )
					.collect ( Collectors.toSet () );
				
				
				AttributeName attYear = getAttributeName ( graph, "YEAR" );
				List<Integer> newPubs = PublicationUtils.newPubsByNumber ( 
					allPubs, 
					attYear, 
					options.getInt ( OPT_DEFAULT_NUMBER_PUBS, -1 ) 
				);

				// add most recent publications here
				if ( !newPubs.isEmpty () )
				{
					String pubString = "Publication__" + allPubs.size () + "__";
					pubString += newPubs.stream ()
						.map ( graph::getConcept )
					  .map ( this::getMolBioDefaultLabel )
					  .map ( name -> name.contains ( "PMID:" ) ? name : "PMID:" + name )
					  .collect ( Collectors.joining ( "//" ) );
					cc2name.put ( "Publication", pubString );
				}

				// create output string for evidences column in GeneView table
				String evidenceStr = cc2name.entrySet ()
				.stream ()
				.map ( e -> 
					"Publication".equals ( e.getKey () )  
						? e.getValue ()
						: e.getKey () + "__" + e.getValue ().split ( "//" ).length + "__" + e.getValue ()
				)
				.collect ( Collectors.joining ( "||" ) );
								
				if ( luceneHits.isEmpty () && listMode.equals ( "GLrestrict" ) ) continue;
				
				if ( ! ( !evidenceStr.isEmpty () || qtls.isEmpty () ) ) continue;
				
				out.append (
					id + "\t" + geneHelper.getBestAccession () + "\t" + geneName + "\t" + geneHelper.getChromosome () + "\t" 
					+ geneHelper.getBeginBP ( true ) + "\t" + geneHelper.getTaxID () + "\t" 
					+ new DecimalFormat ( "0.00" ).format ( score ) + "\t" + (isInList ? "yes" : "no" ) + "\t" + infoQTLStr + "\t" 
					+ evidenceStr + "\n" 
				);

			} // for candidates
			log.info ( "Gene table generated..." );
			return out.toString ();
		
		} // writeGeneTable()


		
    /**
     * Write Evidence Table for Evidence View file
     *
     */
		public String writeEvidenceTable ( 
			String keywords, Map<ONDEXConcept, Float> luceneConcepts, Set<ONDEXConcept> userGenes, List<String> qtlsStr 
		)
		{
      var graph = dataService.getGraph ();
			
			StringBuffer out = new StringBuffer ();
			out.append ( "TYPE\tNAME\tSCORE\tP-VALUE\tGENES\tUSER GENES\tQTLS\tONDEXID\n" );
			
			if ( userGenes == null || userGenes.isEmpty () ) return out.toString ();
			
			var genes2Concepts = semanticMotifService.getGenes2Concepts ();			
			int allGenesSize = genes2Concepts.keySet ().size ();
			int userGenesSize = userGenes.size ();

			log.info ( "generate Evidence table..." );
			List<QTL> qtls = QTL.fromStringList ( qtlsStr );					

			DecimalFormat sfmt = new DecimalFormat ( "0.00" );
			DecimalFormat pfmt = new DecimalFormat ( "0.00000" );

			for ( ONDEXConcept lc : luceneConcepts.keySet () )
			{
				// Creates type,name,score and numberOfGenes
				String type = lc.getOfType ().getId ();
				String name = getMolBioDefaultLabel ( lc );
				// All publications will have the format PMID:15487445
				// if (type == "Publication" && !name.contains("PMID:"))
				// name = "PMID:" + name;
				// Do not print publications or proteins or enzymes in evidence view
				if ( Stream.of ( "Publication", "Protein", "Enzyme" ).anyMatch ( t -> t.equals ( type ) ) ) 
					continue;
				
				var concepts2Genes = semanticMotifService.getConcepts2Genes ();
				var genes2QTLs = semanticMotifService.getGenes2QTLs ();

				Float score = luceneConcepts.get ( lc );
				Integer ondexId = lc.getId ();
				if ( !concepts2Genes.containsKey ( lc.getId () ) ) continue;
				Set<Integer> listOfGenes = concepts2Genes.get ( lc.getId () );
				Integer numberOfGenes = listOfGenes.size ();
				Set<String> userGenesStrings = new HashSet<> ();
				Integer numberOfQTL = 0;

				for ( int log : listOfGenes )
				{
					ONDEXConcept gene = graph.getConcept ( log );
					var geneHelper = new GeneHelper ( graph, gene );
					
					if ( ( userGenes != null ) && ( gene != null ) && ( userGenes.contains ( gene ) ) )
					{
						// numberOfUserGenes++;
						// retain gene Accession/Name (18/07/18)
						userGenesStrings.add ( geneHelper.getBestAccession () );
						
						// This was commented at some point and it's still unclear if needed. Keeping for further verifications

						
						
						// String geneName = getShortestPreferedName(gene.getConceptNames()); geneAcc= geneName;

					}

					if ( genes2QTLs.containsKey ( log ) ) numberOfQTL++;

					String chr = geneHelper.getChromosome ();
					int beg = geneHelper.getBeginBP ( true );
										
					for ( QTL loci : qtls )
					{
						String qtlChrom = loci.getChromosome ();
						Integer qtlStart = loci.getStart ();
						Integer qtlEnd = loci.getEnd ();

						if ( qtlChrom.equals ( chr ) && beg >= qtlStart && beg <= qtlEnd ) numberOfQTL++;
					}

				} // for log

				if ( userGenesStrings.isEmpty () ) continue;
				
				double pvalue = 0.0;

				// quick adjustment to the score to make it a P-value from F-test instead
				int matchedInGeneList = userGenesStrings.size ();
				int notMatchedInGeneList = userGenesSize - matchedInGeneList;
				int matchedNotInGeneList = numberOfGenes - matchedInGeneList;
				int notMatchedNotInGeneList = allGenesSize - matchedNotInGeneList - matchedInGeneList - notMatchedInGeneList;

				FisherExact fisherExact = new FisherExact ( allGenesSize );
				pvalue = fisherExact.getP ( 
					matchedInGeneList, matchedNotInGeneList, notMatchedInGeneList, notMatchedNotInGeneList
				);
				
				var userGenesStr = userGenesStrings.stream ().collect ( Collectors.joining ( "," ) ); 
				out.append ( 
					type + "\t" + name + "\t" + sfmt.format ( score ) + "\t" + pfmt.format ( pvalue ) + "\t"
					+ numberOfGenes + "\t" + userGenesStr + "\t" + numberOfQTL + "\t" + ondexId + "\n" 
				);
			} // for luceneConcepts()
			
			return out.toString ();
		} // writeEvidenceTable()

						
    /**
     * Write Synonym Table for Query suggestor
     *
     */
		public String writeSynonymTable ( String keyword ) throws ParseException
		{
			StringBuffer out = new StringBuffer ();
			// TODO: Lucene shouldn't be used directly
			Analyzer analyzer = new StandardAnalyzer ();
      var graph = dataService.getGraph ();
			
			Set<String> synonymKeys = this.getSearchWords ( keyword );
			for ( var synonymKey: synonymKeys )
			{
				log.info ( "Checking synonyms for \"{}\"", synonymKey );
				if ( synonymKey.contains ( " " ) && !synonymKey.startsWith ( "\"" ) ) 
					synonymKey = "\"" + synonymKey + "\"";

				Map<Integer, Float> synonyms2Scores = new HashMap<> ();

				// search concept names
				String fieldNameCN = getLuceneFieldName ( "ConceptName", null );
				QueryParser parserCN = new QueryParser ( fieldNameCN, analyzer );
				Query qNames = parserCN.parse ( synonymKey );
				ScoredHits<ONDEXConcept> hitSynonyms = searchService.luceneMgr.searchTopConcepts ( qNames, 500 );

        /*
         * TODO: does this still apply?
         * 
         * number of top concepts searched for each Lucene field, increased for now from
         * 100 to 500, until Lucene code is ported from Ondex to KnetMiner, when we'll
         * make changes to the QueryParser code instead.
         */

				for ( ONDEXConcept c : hitSynonyms.getOndexHits () )
				{
					if ( c instanceof LuceneConcept ) c = ( (LuceneConcept) c ).getParent ();
					
					int cid = c.getId ();
					float cscore = hitSynonyms.getScoreOnEntity ( c );
					
					synonyms2Scores.merge ( cid, cscore, Math::max );
				}

				
				if ( synonyms2Scores.isEmpty () ) continue;

				// Only start a KEY tag if it will have contents. Otherwise skip it.
				out.append ( "<" + synonymKey + ">\n" );

				Stream<Map.Entry<Integer, Float>> sortedSynonyms = synonyms2Scores.entrySet ()
				.stream ()
				.sorted ( Collections.reverseOrder ( Map.Entry.comparingByValue () ) );

				Map<String, Integer> entryCountsByType = new HashMap<> ();
				final int MAX_SYNONYMS = 25; // we store this no of top synonyms per concept
						
				// writes the topX values in table
				sortedSynonyms.forEach ( entry -> 
				{
					int synonymId = entry.getKey ();
					float score = entry.getValue ();
					
					ONDEXConcept eoc = graph.getConcept ( synonymId );
					String type = eoc.getOfType ().getId ();

					if ( ( type.equals ( "Publication" ) || type.equals ( "Thing" ) ) ) return;
					
					// TODO: before, this count was incremented in the cNames loop below, however, that way either we
					// get the same because there's one preferred name only,
					// or the count computed that way is likely wrong, cause it increases with names
					//
					int synCount = entryCountsByType.compute ( type, 
						(thisType, thisCount) -> thisType == null ? 1 : ++thisCount
					); 

					if ( synCount > MAX_SYNONYMS ) return;

					
					Set<ConceptName> cNames = eoc.getConceptNames ();

					cNames.stream ()
					.filter ( ConceptName::isPreferred )
					.map ( ConceptName::getName )
					.forEach ( name ->
					{
						// error going around for publication
						// suggestions
						if ( name.contains ( "\n" ) ) name = name.replace ( "\n", "" );

						// error going around for qtl
						// suggestions
						if ( name.contains ( "\"" ) ) name = name.replaceAll ( "\"", "" );
						
						out.append ( name + "\t" + type + "\t" + Float.toString ( score ) + "\t" + synonymId + "\n" );
					});
				}); // forEach synonym

				out.append ( "</" + synonymKey + ">\n" );
					
			} // for synonymKeys
			return out.toString ();
		} //

		
		/**
		 * TODO: this is only used by {@link OndexLocalDataSource} and only to know the size of 
		 * concepts that match. So, do we need to compute the map, or do wee need the count only?
		 * 
		 * The two tasks are different, see below.
		 * 
		 */
		public Map<Integer, Set<Integer>> getMapEvidences2Genes ( Map<ONDEXConcept, Float> luceneConcepts )
		{
			var concepts2Genes = semanticMotifService.getConcepts2Genes ();

			return luceneConcepts.keySet ()
			.stream ()
			.map ( ONDEXConcept::getId )
			.filter ( concepts2Genes::containsKey )
			// .count () As said above, this would be enough if we need a count only
			.collect ( Collectors.toMap ( Function.identity (), concepts2Genes::get ) );
		}

		
    /**
     * Returns the shortest preferred Name from a set of concept Names or ""
     * [Gene|Protein][Phenotype][The rest]
     *
     * @param cns Set<ConceptName>
     * @return String name
     */
    private String getShortestPreferedName ( Set<ConceptName> cns ) 
    {
    	return cns.stream ()
      .filter ( ConceptName::isPreferred )
    	.map ( ConceptName::getName )
    	.map ( String::trim )
    	.sorted ( Comparator.comparing ( String::length ) )
    	.findFirst ()
    	.orElse ( "" );
    }

    /**
     * Returns the shortest not ambiguous accession or ""
     *
     * @param accs Set<ConceptAccession>
     * @return String name
     */
    private String getShortestNotAmbiguousAccession(Set<ConceptAccession> accs) 
    {
    	return accs.stream ()
      .filter ( acc -> !acc.isAmbiguous () )
    	.map ( ConceptAccession::getAccession )
    	.map ( String::trim )
    	.sorted ( Comparator.comparing ( String::length ) )
    	.findFirst ()
    	.orElse ( "" );
    }


    
    /**
     * Returns the best name for certain molecular biology entities, like Gene, Protein, falls back to a default
     * label in the other cases. 
     * 
     */
		private String getMolBioDefaultLabel ( ONDEXConcept c )
		{
			String type = c.getOfType ().getId ();
			String bestAcc = StringUtils.trimToEmpty ( getShortestNotAmbiguousAccession ( c.getConceptAccessions () ) );
			String bestName = StringUtils.trimToEmpty ( getShortestPreferedName ( c.getConceptNames () ) );

			String result = "";
			
			if ( type == "Gene" || type == "Protein" )
			{
				if ( bestAcc.isEmpty () ) result = bestName;
				else result = bestAcc.length () < bestName.length () ? bestAcc : bestName;
			}
			else
				result = !bestName.isEmpty () ? bestName : bestAcc;

			return StringUtils.abbreviate ( result, 30 );
		}

    
        
    /**
     * Returns number of organism (taxID) genes at a given loci
     *
     * @param chr chromosome name as used in GViewer
     * @param start start position
     * @param end end position
     * @return 0 if no genes found, otherwise number of genes at specified loci
     */
		public int getLociGeneCount ( String chr, int start, int end )
		{
			// TODO: should we fail with chr == "" too? Right now "" is considered == "" 
			if ( chr == null ) return 0; 
		
      var graph = dataService.getGraph ();
			
			ConceptClass ccGene =	ONDEXGraphUtils.getConceptClass ( graph, "Gene" );
			Set<ONDEXConcept> genes = graph.getConceptsOfConceptClass ( ccGene );
			
			return (int) genes.stream()
			.map ( gene -> new GeneHelper ( graph, gene ) )
			.filter ( geneHelper -> chr.equals ( geneHelper.getChromosome () ) )
			.filter ( geneHelper -> dataService.containsTaxId ( geneHelper.getTaxID () ) )
			.filter ( geneHelper -> geneHelper.getBeginBP ( true ) >= start )
			.filter ( geneHelper -> geneHelper.getEndBP ( true ) <= end )
			.count ();
		}

    /**
     * TODO: WTH?!? This is an Ondex module utility
     */
    private String getLuceneFieldName ( String name, String value )
    {
    	return value == null ? name : name + "_" + value;
    }

    /**
     * TODO: This is more Lucene module stuff 
     */
    private void luceneConceptSearchHelper ( 
    	String keywords, String fieldName, String fieldValue, int resultLimit, 
    	HashMap<ONDEXConcept, Float> allResults, ScoredHits<ONDEXConcept> notHits, 
    	Analyzer analyzer ) throws ParseException
    {
			fieldName = getLuceneFieldName ( fieldName, fieldValue );
			QueryParser parser = new QueryParser ( fieldName, analyzer );
			Query qAtt = parser.parse ( keywords );
			ScoredHits<ONDEXConcept> thisHits = searchService.luceneMgr.searchTopConcepts ( qAtt, resultLimit );
			mergeHits ( allResults, thisHits, notHits );
    }
   
}