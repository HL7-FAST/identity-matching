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
	private boolean _addressLineMatch;
	private boolean _addressCityMatch;
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
		boolean AddressLineMatch,
		boolean AddressCityMatch,
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
		_addressLineMatch = AddressLineMatch;
		_addressCityMatch = AddressCityMatch;
		_addressPostalCodeMatch = AddressPostalCodeMatch;
	}

	public double scoreMatch() {

		return 0;
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


