package ca.uhn.fhir.jpa.starter.operations.models;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class IdentityMatchingScorer {

	private boolean _ssnMatch;
	private boolean _mrnMatch;
	private boolean _driversLicenseMatch;
	private boolean _passportMatch;
	private boolean _insuranceIdentifierMatch;
	private boolean _familyNameMatch;
	private boolean _givenNameMatch;
	private boolean _genderMatch;
	private boolean _birthDateMatch;
	private boolean _phoneNumberMatch;
	private boolean _emailMatch;
	private boolean _addressLineMatch;
	private boolean _addressCityMatch;
	private boolean _addressStateMatch;
	private boolean _addressPostalCodeMatch;
	private List<String> matchMessages;

	public IdentityMatchingScorer() { matchMessages = new ArrayList<>(); }

	public IdentityMatchingScorer(
		boolean SSNMatch,
		boolean MrnMatch,
		boolean DriversLicenseMatch,
		boolean PassportMatch,
		boolean InsuranceIdentifierMatch,
		boolean FamilyNameMatch,
		boolean GivenNameMatch,
		boolean GenderMatch,
		boolean BirthDateMatch,
		boolean PhoneNumberMatch,
		boolean EmailMatch,
		boolean AddressLineMatch,
		boolean AddressCityMatch,
		boolean AddressStateMatch,
		boolean AddressPostalCodeMatch
	)
	{
		_ssnMatch = SSNMatch;
		_mrnMatch = MrnMatch;
		_driversLicenseMatch = DriversLicenseMatch;
		_passportMatch = PassportMatch;
		_insuranceIdentifierMatch = InsuranceIdentifierMatch;
		_familyNameMatch = FamilyNameMatch;
		_givenNameMatch = GivenNameMatch;
		_genderMatch = GenderMatch;
		_birthDateMatch = BirthDateMatch;
		_phoneNumberMatch = PhoneNumberMatch;
		_emailMatch = EmailMatch;
		_addressLineMatch = AddressLineMatch;
		_addressCityMatch = AddressCityMatch;
		_addressStateMatch = AddressStateMatch;
		_addressPostalCodeMatch = AddressPostalCodeMatch;

		matchMessages = new ArrayList<>();
	}

	// Grading based on table found http://build.fhir.org/ig/HL7/fhir-identity-matching-ig/patient-matching.html#scoring-matches--responders-system-match-output-quality-score
	public double scoreMatch() {

		if(
			(_mrnMatch) ||
			(_givenNameMatch && _familyNameMatch && _driversLicenseMatch) ||
			(_givenNameMatch && _familyNameMatch && _passportMatch) ||
			(_givenNameMatch && _familyNameMatch && _insuranceIdentifierMatch) ||
			(_givenNameMatch && _familyNameMatch && _ssnMatch)
		) { return .99; }
		else if (
			//(_givenNameMatch && _familyNameMatch && _insuranceIdentifierMatch) ||
			(_givenNameMatch && _familyNameMatch && _birthDateMatch && _addressLineMatch && _addressPostalCodeMatch) ||
			(_givenNameMatch && _familyNameMatch && _birthDateMatch && _addressLineMatch && _addressCityMatch && _addressStateMatch) ||
			(_givenNameMatch && _familyNameMatch && _birthDateMatch && _emailMatch)
		) { return .80; }
		else if (
			(_givenNameMatch && _familyNameMatch && _birthDateMatch && _phoneNumberMatch) ||
			(_givenNameMatch && _familyNameMatch && _birthDateMatch && _genderMatch && _addressPostalCodeMatch)
		) { return .70; }
		else if (
			(_givenNameMatch && _familyNameMatch && _birthDateMatch && _genderMatch) ||
			(_givenNameMatch && _familyNameMatch && _birthDateMatch)
		) { return .60; }
		else {
			return .10;
		}
	}

	public List<String> getMatchMessages() {
		return matchMessages.stream().distinct().collect(Collectors.toList());
	}

	//properties
	public boolean getSSNMatch() { return _ssnMatch; }
	public void setSSNMatch(boolean value) {
		final String SocialSecurityNumberMatchMsg = "A matching social security number was found.";
		this._ssnMatch = value;
		if(value) { matchMessages.add(SocialSecurityNumberMatchMsg); }
	}

	public boolean getMrnMatch() { return _mrnMatch; }
	public void setMrnMatch(boolean value) {
		final String MedicalRecordNumberMatchMsg = "A matching medical record number was found.";
		this._mrnMatch = value;
		if(value) { matchMessages.add(MedicalRecordNumberMatchMsg); }
	}

	public boolean getDriversLicenseMatch() { return _driversLicenseMatch; }
	public void setDriversLicenseMatch(boolean value) {
		final String DriversLicenseMatchMsg = "A matching drivers license number was found.";
		this._driversLicenseMatch = value;
		if(value) { matchMessages.add(DriversLicenseMatchMsg); }
	}

	public boolean getPassportMatch() { return _passportMatch; }
	public void setPassportMatch(boolean value) {
		final String PassportNumberMatchMsg = "A matching passport number was found.";
		this._passportMatch = value;
		if(value) { matchMessages.add(PassportNumberMatchMsg); }
	}

	public boolean getInsuranceIdentifierMatch() { return _insuranceIdentifierMatch; }
	public void setInsuranceIdentifierMatch(boolean value) {
		final String InsuranceIdentificationMatchMsg = "A matching insurance identification was found.";
		this._insuranceIdentifierMatch = value;
		if(value) { matchMessages.add(InsuranceIdentificationMatchMsg); }
	}

	public boolean getFamilyNameMatch() { return _familyNameMatch; }
	public void setFamilyNameMatch(boolean value) {
		final String FamilyNameMatchMsg = "A matching last name was found.";
		this._familyNameMatch = value;
		if(value) { matchMessages.add(FamilyNameMatchMsg); }
	}

	public boolean getGivenNameMatch() { return _givenNameMatch; }
	public void setGivenNameMatch(boolean value) {
		final String GivenNameMatchMsg = "A matching first name was found.";
		this._givenNameMatch = value;
		if(value) { matchMessages.add(GivenNameMatchMsg); }
	}

	public boolean getGenderMatch() { return _genderMatch; }
	public void setGenderMatch(boolean value) {
		final String GenderMatchMsg = "A matching gender was found.";
		this._genderMatch = value;
		if(value) { matchMessages.add(GenderMatchMsg); }
	}

	public boolean getBirthDateMatch() { return _birthDateMatch; }
	public void setBirthDateMatch(boolean value) {
		final String BirthDateMatchMsg = "A matching birthdate was found.";
		this._birthDateMatch = value;
		if(value) { matchMessages.add(BirthDateMatchMsg); }
	}

	public boolean getPhoneNumberMatch() { return _phoneNumberMatch; }
	public void setPhoneNumberMatch(boolean value) {
		final String PhoneNumberMatchMsg = "A matching phone number was found.";
		this._phoneNumberMatch = value;
		if(value) { matchMessages.add(PhoneNumberMatchMsg); }
	}

	public boolean getEmailMatch() { return _emailMatch; }
	public void setEmailMatch(boolean value) {
		final String EmailMatchMsg = "A matching email address was found.";
		this._emailMatch = value;
		if(value) { matchMessages.add(EmailMatchMsg); }
	}

	public boolean getAddressLineMatch() { return _addressLineMatch; }
	public void setAddressLineMatch(boolean value) {
		final String AddressLineMatchMsg = "A matching address line of residence was found.";
		this._addressLineMatch = value;
		if(value) { matchMessages.add(AddressLineMatchMsg); }
	}

	public boolean getAddressCityMatch() { return _addressCityMatch; }
	public void setAddressCityMatch(boolean value) {
		final String AddressCityMatchMsg = "A matching city of residence was found.";
		this._addressCityMatch = value;
		if(value) { matchMessages.add(AddressCityMatchMsg); }
	}

	public boolean getAddressStateMatch() { return _addressStateMatch; }
	public void setAddressStateMatch(boolean value) {
		final String AddressStateMatchMsg = "A matching state of residence was found";
		this._addressStateMatch = value;
		if(value) { matchMessages.add(AddressStateMatchMsg); }
	}

	public boolean getAddressPostalCodeMatch() { return _addressPostalCodeMatch; }
	public void setAddressPostalCodeMatch(boolean value) {
		final String PostalCodeMatchMsg = "A matching postal code of residence was found.";
		this._addressPostalCodeMatch = value;
		if(value) { matchMessages.add(PostalCodeMatchMsg); }
	}

}


