package ru.ultimavox.itsm.assetmanagement.domain;
import static org.assertj.core.api.Assertions.*; import java.time.LocalDate; import java.util.UUID; import org.junit.jupiter.api.Test;
class AssetTest { private Asset stock(){return new Asset(UUID.randomUUID(),"AST-0042",Asset.Kind.LAPTOP,Asset.Status.IN_STOCK,null,null,LocalDate.now(),LocalDate.now().plusYears(3));}
 @Test void assignment_moves_stock_asset_into_use(){var assigned=stock().assignTo("user-42");assertThat(assigned.status()).isEqualTo(Asset.Status.IN_USE);assertThat(assigned.ownerSubject()).isEqualTo("user-42");}
 @Test void_retired_assets_cannot_return_to_stock(){var retired=stock().transitionTo(Asset.Status.RETIRED);assertThatThrownBy(()->retired.transitionTo(Asset.Status.IN_STOCK)).isInstanceOf(IllegalStateException.class);}
}
