MATCH path = (gene_1:Gene{ iri: $startIri })
  - [enc_1_10:enc] - (protein_10:Protein)
  - [rel_10_10_d_2:genetic|physical*0..2] -> (protein_10b:Protein)
  - [enc_10_9:enc] - (gene_9:Gene)
  - [rel_9_13:cooc_wi|has_observ_pheno] - (phenotype_13:Phenotype)
RETURN path