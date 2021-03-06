MATCH path = (gene_1:Gene)
  - [ortho_1_9:ortho] - (gene_9:Gene)
  - [has_function_9_3:has_function] - (molFunc_3:MolFunc)
WHERE gene_1.iri IN $startGeneIris
RETURN path