package io.github.amir01mn.parking.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** Read-only university directory row: maps an e-mail to its pricing tier. */
@Entity
@Immutable
@Table(name = "university_directory")
public class DirectoryEntry {

    @Id
    @Column(length = 254)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", nullable = false, length = 20)
    private UserType userType;

    protected DirectoryEntry() {
        // for JPA
    }

    public String getEmail() {
        return email;
    }

    public UserType getUserType() {
        return userType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DirectoryEntry other)) {
            return false;
        }
        return email != null && email.equals(other.getEmail());
    }

    @Override
    public int hashCode() {
        return DirectoryEntry.class.hashCode();
    }

    @Override
    public String toString() {
        return "DirectoryEntry[email=" + email + ", userType=" + userType + "]";
    }
}
