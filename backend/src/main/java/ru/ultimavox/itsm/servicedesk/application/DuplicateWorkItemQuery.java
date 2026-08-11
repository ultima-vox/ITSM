package ru.ultimavox.itsm.servicedesk.application;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem;

@Service
public class DuplicateWorkItemQuery {
  private final WorkItemStore store;
  DuplicateWorkItemQuery(WorkItemStore store) { this.store=store; }

  public List<Match> find(String title,String description,UUID excludeId,int limit) {
    int safeLimit=Math.min(Math.max(limit,1),20);
    return store.duplicateCandidates(300).stream()
        .filter(item -> excludeId==null || !item.id().equals(excludeId))
        .map(item -> match(item,title,description))
        .filter(match -> match.score()>=0.25)
        .sorted((a,b) -> Double.compare(b.score(),a.score()))
        .limit(safeLimit).toList();
  }

  static Match match(WorkItem item,String title,String description) {
    double titleScore=jaccard(tokens(title),tokens(item.title()));
    double descriptionScore=jaccard(tokens(description),tokens(item.description()));
    double score=Math.round((titleScore*0.7+descriptionScore*0.3)*1000.0)/1000.0;
    String reason=titleScore>=0.7 ? "TITLE_HIGH" : descriptionScore>=0.7 ? "DESCRIPTION_HIGH" : "COMBINED";
    return new Match(item.id(),item.number(),item.title(),item.state().name(),item.priority().name(),score,reason);
  }
  private static Set<String> tokens(String value) {
    if(value==null||value.isBlank()) return Set.of();
    String normalized=Normalizer.normalize(value,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
        .replaceAll("[^\\p{L}\\p{N}]+"," ").trim();
    if(normalized.isEmpty()) return Set.of();
    Set<String> result=new HashSet<>(Arrays.asList(normalized.split("\\s+")));
    result.removeIf(token -> token.length()<2); return result;
  }
  private static double jaccard(Set<String> left,Set<String> right) {
    if(left.isEmpty()||right.isEmpty()) return 0;
    Set<String> intersection=new HashSet<>(left); intersection.retainAll(right);
    Set<String> union=new HashSet<>(left); union.addAll(right);
    return (double)intersection.size()/union.size();
  }
  public record Match(UUID id,String number,String title,String state,String priority,double score,String reason) {}
}
