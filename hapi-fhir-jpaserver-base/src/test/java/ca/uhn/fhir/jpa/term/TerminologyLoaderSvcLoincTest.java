package ca.uhn.fhir.jpa.term;

import ca.uhn.fhir.jpa.entity.TermCodeSystemVersion;
import ca.uhn.fhir.jpa.entity.TermConcept;
import ca.uhn.fhir.jpa.term.loinc.LoincDocumentOntologyHandler;
import ca.uhn.fhir.jpa.term.loinc.LoincPartHandler;
import ca.uhn.fhir.jpa.term.loinc.LoincPartRelatedCodeMappingHandler;
import ca.uhn.fhir.jpa.term.loinc.LoincRsnaPlaybookHandler;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.util.TestUtil;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.ConceptMap;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.ValueSet;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.contains;
import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class TerminologyLoaderSvcLoincTest {
	private static final org.slf4j.Logger ourLog = org.slf4j.LoggerFactory.getLogger(TerminologyLoaderSvcLoincTest.class);
	private TerminologyLoaderSvcImpl mySvc;

	@Mock
	private IHapiTerminologySvc myTermSvc;

	@Mock
	private IHapiTerminologySvcDstu3 myTermSvcDstu3;

	@Captor
	private ArgumentCaptor<TermCodeSystemVersion> myCsvCaptor;
	@Captor
	private ArgumentCaptor<CodeSystem> mySystemCaptor;
	@Mock
	private RequestDetails details;
	@Captor
	private ArgumentCaptor<List<ValueSet>> myValueSetsCaptor;
	@Captor
	private ArgumentCaptor<List<ConceptMap>> myConceptMapCaptor;
	private ZipCollectionBuilder myFiles;


	@Before
	public void before() {
		mySvc = new TerminologyLoaderSvcImpl();
		mySvc.setTermSvcForUnitTests(myTermSvc);
		mySvc.setTermSvcDstu3ForUnitTest(myTermSvcDstu3);
		
		myFiles = new ZipCollectionBuilder();
	}

	@Test
	public void testLoadLoinc() throws Exception {
		myFiles.addFile("/loinc/", "loinc.csv", TerminologyLoaderSvcImpl.LOINC_FILE);
		myFiles.addFile("/loinc/", "hierarchy.csv", TerminologyLoaderSvcImpl.LOINC_HIERARCHY_FILE);
		myFiles.addFile("/loinc/", "AnswerList_Beta_1.csv", TerminologyLoaderSvcImpl.LOINC_ANSWERLIST_FILE);
		myFiles.addFile("/loinc/", TerminologyLoaderSvcImpl.LOINC_ANSWERLIST_LINK_FILE, TerminologyLoaderSvcImpl.LOINC_ANSWERLIST_LINK_FILE);
		myFiles.addFile("/loinc/", TerminologyLoaderSvcImpl.LOINC_PART_FILE, TerminologyLoaderSvcImpl.LOINC_PART_FILE);
		myFiles.addFile("/loinc/", TerminologyLoaderSvcImpl.LOINC_PART_LINK_FILE, TerminologyLoaderSvcImpl.LOINC_PART_LINK_FILE);
		myFiles.addFile("/loinc/", TerminologyLoaderSvcImpl.LOINC_PART_RELATED_CODE_MAPPING_FILE);
		myFiles.addFile("/loinc/", TerminologyLoaderSvcImpl.LOINC_DOCUMENT_ONTOLOGY_FILE);
		myFiles.addFile("/loinc/", TerminologyLoaderSvcImpl.LOINC_RSNA_PLAYBOOK_FILE);

		// Actually do the load
		mySvc.loadLoinc(myFiles.getFiles(), details);

		verify(myTermSvcDstu3, times(1)).storeNewCodeSystemVersion(mySystemCaptor.capture(), myCsvCaptor.capture(), any(RequestDetails.class), myValueSetsCaptor.capture(), myConceptMapCaptor.capture());

		TermCodeSystemVersion ver = myCsvCaptor.getValue();

		Map<String, TermConcept> concepts = new HashMap<>();
		for (TermConcept next : ver.getConcepts()) {
			concepts.put(next.getCode(), next);
		}
		Map<String, ValueSet> valueSets = new HashMap<>();
		for (ValueSet next : myValueSetsCaptor.getValue()) {
			valueSets.put(next.getId(), next);
		}
		Map<String, ConceptMap> conceptMaps = new HashMap<>();
		for (ConceptMap next : myConceptMapCaptor.getAllValues().get(0)) {
			conceptMaps.put(next.getId(), next);
		}
		ConceptMap conceptMap;
		TermConcept code;
		ValueSet vs;
		ConceptMap.ConceptMapGroupComponent group;

		// Normal loinc code
		code = concepts.get("10013-1");
		assertEquals("10013-1", code.getCode());
		assertEquals("Elpot", code.getStringProperty("PROPERTY"));
		assertEquals("Pt", code.getStringProperty("TIME_ASPCT"));
		assertEquals("R' wave amplitude in lead I", code.getDisplay());

		// Loinc code with answer
		code = concepts.get("61438-8");
		assertThat(code.getStringProperties("answer-list"), contains("LL1000-0"));

		// Answer list
		code = concepts.get("LL1001-8");
		assertEquals("LL1001-8", code.getCode());
		assertEquals("PhenX05_14_30D freq amts", code.getDisplay());

		// Answer list code
		code = concepts.get("LA13834-9");
		assertEquals("LA13834-9", code.getCode());
		assertEquals("1-2 times per week", code.getDisplay());
		assertEquals(3, code.getSequence().intValue());

		// Answer list code with link to answers-for
		code = concepts.get("LL1000-0");
		assertThat(code.getStringProperties("answers-for"), contains("61438-8"));

		// AnswerList valueSet
		vs = valueSets.get("LL1001-8");
		assertEquals(IHapiTerminologyLoaderSvc.LOINC_URL, vs.getIdentifier().get(0).getSystem());
		assertEquals("LL1001-8", vs.getIdentifier().get(0).getValue());
		assertEquals("PhenX05_14_30D freq amts", vs.getName());
		assertEquals("urn:oid:1.3.6.1.4.1.12009.10.1.166", vs.getUrl());
		assertEquals(1, vs.getCompose().getInclude().size());
		assertEquals(7, vs.getCompose().getInclude().get(0).getConcept().size());
		assertEquals(IHapiTerminologyLoaderSvc.LOINC_URL, vs.getCompose().getInclude().get(0).getSystem());
		assertEquals("LA6270-8", vs.getCompose().getInclude().get(0).getConcept().get(0).getCode());
		assertEquals("Never", vs.getCompose().getInclude().get(0).getConcept().get(0).getDisplay());

		// Part
		code = concepts.get("LP101394-7");
		assertEquals("LP101394-7", code.getCode());
		assertEquals("adjusted for maternal weight", code.getDisplay());

		// Part Mappings
		conceptMap = conceptMaps.get(LoincPartRelatedCodeMappingHandler.LOINC_TO_SNOMED_CM_ID);
		assertEquals(IHapiTerminologyLoaderSvc.LOINC_URL, conceptMap.getSourceCanonicalType().getValueAsString());
		assertEquals(IHapiTerminologyLoaderSvc.SCT_URL, conceptMap.getTargetCanonicalType().getValueAsString());
		assertEquals("This material includes SNOMED Clinical Terms® (SNOMED CT®) which is used by permission of the International Health Terminology Standards Development Organisation (IHTSDO) under license. All rights reserved. SNOMED CT® was originally created by The College of American Pathologists. “SNOMED” and “SNOMED CT” are registered trademarks of the IHTSDO.This material includes content from the US Edition to SNOMED CT, which is developed and maintained by the U.S. National Library of Medicine and is available to authorized UMLS Metathesaurus Licensees from the UTS Downloads site at https://uts.nlm.nih.gov.Use of SNOMED CT content is subject to the terms and conditions set forth in the SNOMED CT Affiliate License Agreement. It is the responsibility of those implementing this product to ensure they are appropriately licensed and for more information on the license, including how to register as an Affiliate Licensee, please refer to http://www.snomed.org/snomed-ct/get-snomed-ct or info@snomed.org<mailto:info@snomed.org>.  This may incur a fee in SNOMED International non-Member countries.", conceptMap.getCopyright());
		assertEquals(1, conceptMap.getGroup().size());
		group = conceptMap.getGroup().get(0);
		assertEquals(IHapiTerminologyLoaderSvc.LOINC_URL, group.getSource());
		assertEquals(IHapiTerminologyLoaderSvc.SCT_URL, group.getTarget());
		assertEquals("http://snomed.info/sct/900000000000207008/version/20170731", group.getTargetVersion());
		assertEquals("LP18172-4", group.getElement().get(0).getCode());
		assertEquals("Interferon.beta", group.getElement().get(0).getDisplay());
		assertEquals(1, group.getElement().get(0).getTarget().size());
		assertEquals("420710006", group.getElement().get(0).getTarget().get(0).getCode());
		assertEquals("Interferon beta (substance)", group.getElement().get(0).getTarget().get(0).getDisplay());

		// Document Ontology ValueSet
		vs = valueSets.get(LoincDocumentOntologyHandler.DOCUMENT_ONTOLOGY_CODES_VS_ID);
		assertEquals(LoincDocumentOntologyHandler.DOCUMENT_ONTOLOGY_CODES_VS_NAME, vs.getName());
		assertEquals(LoincDocumentOntologyHandler.DOCUMENT_ONTOLOGY_CODES_VS_URI, vs.getUrl());
		assertEquals(1, vs.getCompose().getInclude().size());
		assertEquals(IHapiTerminologyLoaderSvc.LOINC_URL, vs.getCompose().getInclude().get(0).getSystem());
		assertEquals(3, vs.getCompose().getInclude().get(0).getConcept().size());
		assertEquals("11488-4", vs.getCompose().getInclude().get(0).getConcept().get(0).getCode());
		assertEquals("Consult note", vs.getCompose().getInclude().get(0).getConcept().get(0).getDisplay());

		// Document ontology parts
		code = concepts.get("11488-4");
		assertEquals(1, code.getCodingProperties("document-kind").size());
		assertEquals(IHapiTerminologyLoaderSvc.LOINC_URL, code.getCodingProperties("document-kind").get(0).getSystem());
		assertEquals("LP173418-7", code.getCodingProperties("document-kind").get(0).getCode());
		assertEquals("Note", code.getCodingProperties("document-kind").get(0).getDisplay());

		// RSNA Playbook ValueSet
		vs = valueSets.get(LoincRsnaPlaybookHandler.RSNA_CODES_VS_ID);
		assertEquals(LoincRsnaPlaybookHandler.RSNA_CODES_VS_NAME, vs.getName());
		assertEquals(LoincRsnaPlaybookHandler.RSNA_CODES_VS_URI, vs.getUrl());
		assertEquals(1, vs.getCompose().getInclude().size());
		assertEquals(3, vs.getCompose().getInclude().get(0).getConcept().size());
		assertEquals(IHapiTerminologyLoaderSvc.LOINC_URL, vs.getCompose().getInclude().get(0).getSystem());
		assertEquals("17787-3", vs.getCompose().getInclude().get(0).getConcept().get(0).getCode());
		assertEquals("NM Thyroid gland Study report", vs.getCompose().getInclude().get(0).getConcept().get(0).getDisplay());

		// RSNA Playbook Code Parts - Region Imaged
		code = concepts.get("17787-3");
		String propertyName = "rad-anatomic-location-region-imaged";
		assertEquals(1, code.getCodingProperties(propertyName).size());
		assertEquals(IHapiTerminologyLoaderSvc.LOINC_URL, code.getCodingProperties(propertyName).get(0).getSystem());
		assertEquals("LP199995-4", code.getCodingProperties(propertyName).get(0).getCode());
		assertEquals("Neck", code.getCodingProperties(propertyName).get(0).getDisplay());
		// RSNA Playbook Code Parts - Imaging Focus
		code = concepts.get("17787-3");
		propertyName = "rad-anatomic-location-imaging-focus";
		assertEquals(1, code.getCodingProperties(propertyName).size());
		assertEquals(IHapiTerminologyLoaderSvc.LOINC_URL, code.getCodingProperties(propertyName).get(0).getSystem());
		assertEquals("LP206648-0", code.getCodingProperties(propertyName).get(0).getCode());
		assertEquals("Thyroid gland", code.getCodingProperties(propertyName).get(0).getDisplay());
		// RSNA Playbook Code Parts - Modality Type
		code = concepts.get("17787-3");
		propertyName = "rad-modality-modality-type";
		assertEquals(1, code.getCodingProperties(propertyName).size());
		assertEquals(IHapiTerminologyLoaderSvc.LOINC_URL, code.getCodingProperties(propertyName).get(0).getSystem());
		assertEquals("LP208891-4", code.getCodingProperties(propertyName).get(0).getCode());
		assertEquals("NM", code.getCodingProperties(propertyName).get(0).getDisplay());

		// RSNA Playbook - LOINC Part -> RadLex RID Mappings
		conceptMap = conceptMaps.get(LoincRsnaPlaybookHandler.RID_MAPPING_CM_ID);
		assertEquals(LoincRsnaPlaybookHandler.RID_MAPPING_CM_URI, conceptMap.getUrl());
		assertEquals(LoincRsnaPlaybookHandler.RID_MAPPING_CM_NAME, conceptMap.getName());
		assertEquals(1, conceptMap.getGroup().size());
		group = conceptMap.getGroupFirstRep();
		// all entries have the same source and target so these should be null
		assertEquals(null, group.getSource());
		assertEquals(null, group.getTarget());
		assertEquals("LP199995-4", group.getElement().get(0).getCode());
		assertEquals("Neck", group.getElement().get(0).getDisplay());
		assertEquals(1, group.getElement().get(0).getTarget().size());
		assertEquals("RID7488", group.getElement().get(0).getTarget().get(0).getCode());
		assertEquals("neck", group.getElement().get(0).getTarget().get(0).getDisplay());
		assertEquals(Enumerations.ConceptMapEquivalence.EQUAL, group.getElement().get(0).getTarget().get(0).getEquivalence());

		// RSNA Playbook - LOINC Term -> RadLex RPID Mappings
		conceptMap = conceptMaps.get(LoincRsnaPlaybookHandler.RPID_MAPPING_CM_ID);
		assertEquals(LoincRsnaPlaybookHandler.RPID_MAPPING_CM_URI, conceptMap.getUrl());
		assertEquals(LoincRsnaPlaybookHandler.RPID_MAPPING_CM_NAME, conceptMap.getName());
		assertEquals(1, conceptMap.getGroup().size());
		group = conceptMap.getGroupFirstRep();
		// all entries have the same source and target so these should be null
		assertEquals(null, group.getSource());
		assertEquals(null, group.getTarget());
		assertEquals("24531-6", group.getElement().get(0).getCode());
		assertEquals("US Retroperitoneum", group.getElement().get(0).getDisplay());
		assertEquals(1, group.getElement().get(0).getTarget().size());
		assertEquals("RPID2142", group.getElement().get(0).getTarget().get(0).getCode());
		assertEquals("US Retroperitoneum", group.getElement().get(0).getTarget().get(0).getDisplay());
		assertEquals(Enumerations.ConceptMapEquivalence.EQUAL, group.getElement().get(0).getTarget().get(0).getEquivalence());


	}



	@AfterClass
	public static void afterClassClearContext() {
		TestUtil.clearAllStaticFieldsForUnitTest();
	}

}
