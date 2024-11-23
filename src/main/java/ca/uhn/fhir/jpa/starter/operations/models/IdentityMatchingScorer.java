package ca.uhn.fhir.jpa.starter.operations.models;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class IdentityMatchingScorer {

	private boolean _ssnMatch;
	private boolean _ssnLast4Match;
	private boolean _mrnMatch;
	private boolean _digitalIdentifierMatch;
	private boolean _driversLicenseMatch;
	private boolean _passportMatch;
	private boolean _insuranceSubscriberMatch;
	private boolean _insuranceMemberMatch;
	private boolean _familyNameMatch;
	private boolean _firstNameMatch;
	private boolean _middleNameMatch;
	private boolean _middleInitialMatch;
	private boolean _birthSexMatch;
	private boolean _birthDateMatch;
	private boolean _phoneNumberMatch;
	private boolean _emailMatch;
	private boolean _addressLineMatch;
	private boolean _addressCityMatch;
	private boolean _addressStateMatch;
	private boolean _addressPostalCodeMatch;
	private List<String> matchMessages;
	private Integer weight;

	public IdentityMatchingScorer() { 
		matchMessages = new ArrayList<>(); 
		weight = 0;
	}

	// Grading based on table found http://build.fhir.org/ig/HL7/fhir-identity-matching-ig/patient-matching.html#scoring-matches--responders-system-match-output-quality-score
	public double scoreMatch() {

		if(
			(_mrnMatch || _digitalIdentifierMatch) ||
			(_firstNameMatch && _familyNameMatch && _driversLicenseMatch) ||
			(_firstNameMatch && _familyNameMatch && _passportMatch) ||
			(_firstNameMatch && _familyNameMatch && _insuranceMemberMatch) ||
			(_firstNameMatch && _familyNameMatch && _birthDateMatch && _insuranceSubscriberMatch) ||
			(_firstNameMatch && _familyNameMatch && _ssnMatch)
		) { return .99; }
		else if (
			(_firstNameMatch && _familyNameMatch && _insuranceSubscriberMatch) ||
			(_firstNameMatch && _familyNameMatch && _birthDateMatch && _addressLineMatch && _addressPostalCodeMatch) ||
			(_firstNameMatch && _familyNameMatch && _birthDateMatch && _addressLineMatch && _addressCityMatch && _addressStateMatch) ||
			(_firstNameMatch && _familyNameMatch && _birthDateMatch && _emailMatch)
		) { return .80; }
		else if (
			(_firstNameMatch && _familyNameMatch && _birthDateMatch && _birthSexMatch && _ssnLast4Match) ||
			(_firstNameMatch && _familyNameMatch && _birthDateMatch && _birthSexMatch && _phoneNumberMatch) ||
			(_firstNameMatch && _familyNameMatch && _birthDateMatch && _birthSexMatch && _addressPostalCodeMatch) ||
			(_firstNameMatch && _familyNameMatch && _birthDateMatch && _birthSexMatch && _middleNameMatch) ||
			(_firstNameMatch && _familyNameMatch && _birthDateMatch && _phoneNumberMatch)
		) { return .70; }
		else if (
			(_firstNameMatch && _familyNameMatch && _birthDateMatch && _birthSexMatch && _middleInitialMatch) ||
			(_firstNameMatch && _familyNameMatch && _birthDateMatch && _birthSexMatch) ||
			(_firstNameMatch && _familyNameMatch && _birthDateMatch)
		) { return .60; }
		else {
			return .10;
		}
	}

	public Integer getMatchWeight() {
		//check for weighted combinations
		weight = 0;
		if(getPassportMatch()) { weight += 10;	}
		if(getDriversLicenseMatch()) { weight += 10; }
		if(
			(getAddressLineMatch() && getAddressPostalCodeMatch()) ||
			(getAddressCityMatch() && getAddressStateMatch()) ||
			getPhoneNumberMatch() ||
			getEmailMatch() ||
			getSSNMatch() ||
			getInsuranceSubscriberMatch() ||
			getMrnMatch()
		) { weight += 5; }
		if(getFirstNameMatch() && getFamilyNameMatch()) { weight += 3; }
		if(getBirthDateMatch()) { weight += 2; }

		return weight;
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

	public boolean getSSNLast4Match() { return _ssnLast4Match; }
	public void setSSNLast4Match(boolean value) {
		final String SocialSecurityNumberLast4MatchMsg = "A matching social security number (last 4 digits) was found.";
		this._ssnLast4Match = value;
		if(value) { matchMessages.add(SocialSecurityNumberLast4MatchMsg); }
	}

	public boolean getMrnMatch() { return _mrnMatch; }
	public void setMrnMatch(boolean value) {
		final String MedicalRecordNumberMatchMsg = "A matching medical record number was found.";
		this._mrnMatch = value;
		if(value) { matchMessages.add(MedicalRecordNumberMatchMsg); }
	}

	public boolean getDigitalIdentifierMatch() { return _digitalIdentifierMatch; }
	public void setDigitalIdentifierMatch(boolean value) {
		final String DigitalIdentifierMatchMsg = "A matching digital identifier was found.";
		this._digitalIdentifierMatch = value;
		if(value) { matchMessages.add(DigitalIdentifierMatchMsg); }
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

	public boolean getInsuranceSubscriberMatch() { return _insuranceSubscriberMatch; }
	public void setInsuranceSubscriberMatch(boolean value) {
		final String InsuranceSubscriberMatchMsg = "A matching insurance subscriber was found.";
		this._insuranceSubscriberMatch = value;
		if(value) { matchMessages.add(InsuranceSubscriberMatchMsg); }
	}

	public boolean getInsuranceMemberMatch() { return _insuranceMemberMatch; }
	public void setInsuranceMemberMatch(boolean value) {
		final String InsuranceMemberMatchMsg = "A matching insurance member was found.";
		this._insuranceMemberMatch = value;
		if(value) { matchMessages.add(InsuranceMemberMatchMsg); }
	}

	public boolean getFamilyNameMatch() { return _familyNameMatch; }
	public void setFamilyNameMatch(boolean value) {
		final String FamilyNameMatchMsg = "A matching last name was found.";
		this._familyNameMatch = value;
		if(value) { matchMessages.add(FamilyNameMatchMsg); }
	}

	public boolean getFirstNameMatch() { return _firstNameMatch; }
	public void setFirstNameMatch(boolean value) {
		final String FirstNameMatchMsg = "A matching first name was found.";
		this._firstNameMatch = value;
		if(value) { matchMessages.add(FirstNameMatchMsg); }
	}

	public boolean getMiddleNameMatch() { return _middleNameMatch; }
	public void setMiddleNameMatch(boolean value) {
		final String MiddleNameMatchMsg = "A matching middle name was found.";
		this._middleNameMatch = value;
		if(value) { matchMessages.add(MiddleNameMatchMsg); }
	}

	public boolean getMiddleInitialMatch() { return _middleInitialMatch; }
	public void setMiddleInitialMatch(boolean value) {
		final String MiddleInitialMatchMsg = "A matching middle initial was found.";
		this._middleInitialMatch = value;
		if(value) { matchMessages.add(MiddleInitialMatchMsg); }
	}

	public boolean getBirthSexMatch() { return _birthSexMatch; }
	public void setBirthSexMatch(boolean value) {
		final String BirthSexMatchMsg = "A matching birth sex was found.";
		this._birthSexMatch = value;
		if(value) { matchMessages.add(BirthSexMatchMsg); }
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


