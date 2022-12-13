package ca.uhn.fhir.jpa.starter.operations.models;

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

	public IdentityMatchingScorer() { }

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
			(_givenNameMatch && _familyNameMatch && _insuranceIdentifierMatch) ||
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

	//properties
	public boolean getSSNMatch() { return _ssnMatch; }
	public void setSSNMatch(boolean value) { this._ssnMatch = value; }

	public boolean getMrnMatch() { return _mrnMatch; }
	public void setMrnMatch(boolean value) { this._mrnMatch = value; }

	public boolean getDriversLicenseMatch() { return _driversLicenseMatch; }
	public void setDriversLicenseMatch(boolean value) { this._driversLicenseMatch = value; }

	public boolean getPassportMatch() { return _passportMatch; }
	public void setPassportMatch(boolean value) { this._passportMatch = value; }

	public boolean getInsuranceIdentifierMatch() { return _insuranceIdentifierMatch; }
	public void setInsuranceIdentifierMatch(boolean value) { this._insuranceIdentifierMatch = value; }

	public boolean getFamilyNameMatch() { return _familyNameMatch; }
	public void setFamilyNameMatch(boolean value) { this._familyNameMatch = value; }

	public boolean getGivenNameMatch() { return _givenNameMatch; }
	public void setGivenNameMatch(boolean value) { this._givenNameMatch = value; }

	public boolean getGenderMatch() { return _genderMatch; }
	public void setGenderMatch(boolean value) { this._genderMatch = value; }

	public boolean getBirthDateMatch() { return _birthDateMatch; }
	public void setBirthDateMatch(boolean value) { this._birthDateMatch = value; }

	public boolean getPhoneNumberMatch() { return _phoneNumberMatch; }
	public void setPhoneNumberMatch(boolean value) { this._phoneNumberMatch = value; }

	public boolean getAddressLineMatch() { return _addressLineMatch; }
	public void setAddressLineMatch(boolean value) { this._addressLineMatch = value; }

	public boolean getAddressCityMatch() { return _addressCityMatch; }
	public void setAddressCityMatch(boolean value) { this._addressCityMatch = value; }

	public boolean getAddressPostalCodeMatch() { return _addressPostalCodeMatch; }
	public void setAddressPostalCodeMatch(boolean value) { this._addressPostalCodeMatch = value; }

}


