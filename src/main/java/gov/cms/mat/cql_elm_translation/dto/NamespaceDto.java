package gov.cms.mat.cql_elm_translation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NamespaceDto {
  private String namespaceCanonical;
  private String namespacePrefix;
}
