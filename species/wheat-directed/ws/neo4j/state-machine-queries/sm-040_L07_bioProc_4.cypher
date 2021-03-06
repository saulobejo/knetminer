MATCH path = (gene_1:Gene)
  - [enc_1_10_d:enc] -> (protein_10:Protein)
  - [rel_10_10:h_s_s|ortho|xref*0..1] - (protein_10b:Protein)
  - [has_domain_10_11_d:has_domain] -> (protDomain_11:ProtDomain)
  - [participates_in_11_4_d:participates_in] -> (bioProc_4:BioProc)
WHERE gene_1.iri IN $startGeneIris
RETURN path