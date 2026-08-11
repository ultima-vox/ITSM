package ru.ultimavox.itsm.servicedesk.application;
import static org.assertj.core.api.Assertions.assertThat; import java.time.Instant; import java.util.UUID; import org.junit.jupiter.api.Test; import ru.ultimavox.itsm.servicedesk.domain.WorkItem;
class DuplicateWorkItemQueryTest {
 @Test void identicalTitlesRankHigh(){var match=DuplicateWorkItemQuery.match(item("VPN connection fails","Remote access error"),"VPN connection fails","Remote access error");assertThat(match.score()).isEqualTo(1.0);assertThat(match.reason()).isEqualTo("TITLE_HIGH");}
 @Test void unrelatedTextRanksZero(){var match=DuplicateWorkItemQuery.match(item("Printer jam","Paper stuck"),"VPN timeout","Cannot connect remotely");assertThat(match.score()).isZero();}
 @Test void normalizationIsCaseAndPunctuationInsensitive(){var match=DuplicateWorkItemQuery.match(item("Ошибка: VPN!",""),"ОШИБКА vpn"," ");assertThat(match.score()).isEqualTo(0.7);}
 private WorkItem item(String title,String description){Instant now=Instant.parse("2026-08-11T00:00:00Z");return new WorkItem(UUID.randomUUID(),"INC-1",WorkItem.Type.INCIDENT,title,description,"Network",WorkItem.State.NEW,WorkItem.Priority.MEDIUM,WorkItem.Impact.MEDIUM,WorkItem.Urgency.MEDIUM,null,"user",null,null,null,false,now,now,null);}
}
