package ca.bc.gov.nrs.ilcr.millmaintenance;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * One selectable contact for a mill's head-office or division slot — a {@code THE.CLIENT_CONTACT}
 * row on the mill's own client location (AD-3).
 *
 * <p>The id is the stored value and the name is the label, which is how legacy's two selects were
 * bound (mills.xhtml:75-78, :90-93).
 */
@Table(schema = "THE", name = "CLIENT_CONTACT")
public record ContactOptionEntity(
    @Id @Column("CLIENT_CONTACT_ID") long clientContactId,
    @Column("CONTACT_NAME") String contactName) {}
