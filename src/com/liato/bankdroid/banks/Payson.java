package com.liato.bankdroid.banks;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.message.BasicNameValuePair;

import android.content.Context;
import android.text.Html;
import android.text.InputType;

import com.liato.bankdroid.Account;
import com.liato.bankdroid.Bank;
import com.liato.bankdroid.BankException;
import com.liato.bankdroid.Helpers;
import com.liato.bankdroid.LoginException;
import com.liato.bankdroid.R;
import com.liato.bankdroid.Transaction;
import com.liato.urllib.Urllib;

public class Payson extends Bank {
	private static final String TAG = "Payson";
	private static final String NAME = "Payson";
	private static final String NAME_SHORT = "payson";
	private static final String URL = "https://nettbank.edb.com/Logon/index.jsp?domain=0066&from_page=http://www.okq8.se&to_page=https://nettbank.edb.com/cardpayment/transigo/logon/done/okq8";
	private static final int BANKTYPE_ID = Bank.PAYSON;
    private static final int INPUT_TYPE_USERNAME = InputType.TYPE_CLASS_TEXT | + InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS;
	
    private Pattern reEventValidation = Pattern.compile("__EVENTVALIDATION\"\\s+value=\"([^\"]+)\"");
    private Pattern reViewState = Pattern.compile("__VIEWSTATE\"\\s+value=\"([^\"]+)\"");
    private Pattern reBalance = Pattern.compile("Saldo:\\s*<strong>([^<]+)<", Pattern.CASE_INSENSITIVE);
	private Pattern reTransactions = Pattern.compile("href=\"details/Default\\.aspx\\?\\d{1,}\">\\s*<span\\s*title=\"(\\d{4}-\\d{2}-\\d{2})[^\"]+\">.*?Grid1_0_3_\\d{1,}_Hy[^>]+>([^<]+)<.*?Grid1_0_5_\\d{1,}_Hy[^>]+>([^<]+)<", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
	private String response = null;
	
	public Payson(Context context) {
		super(context);
		super.TAG = TAG;
		super.NAME = NAME;
		super.NAME_SHORT = NAME_SHORT;
		super.BANKTYPE_ID = BANKTYPE_ID;
		super.URL = URL;
		super.INPUT_TYPE_USERNAME = INPUT_TYPE_USERNAME;
	}

	public Payson(String username, String password, Context context) throws BankException, LoginException {
		this(context);
		this.update(username, password);
	}

	public Urllib login() throws LoginException, BankException {
		urlopen = new Urllib(true);
		Matcher matcher;
		
		try {
			response = urlopen.open("https://www.payson.se/signin/");
            matcher = reViewState.matcher(response);
            if (!matcher.find()) {
                throw new BankException(res.getText(R.string.unable_to_find).toString()+" ViewState.");
            }
            String strViewState = matcher.group(1);
            matcher = reEventValidation.matcher(response);
            if (!matcher.find()) {
                throw new BankException(res.getText(R.string.unable_to_find).toString()+" EventValidation.");
            }
            String strEventValidation = matcher.group(1);

            List <NameValuePair> postData = new ArrayList <NameValuePair>();
			postData.add(new BasicNameValuePair("__LASTFOCUS", ""));
			postData.add(new BasicNameValuePair("__EVENTTARGET", ""));
			postData.add(new BasicNameValuePair("__EVENTARGUMENT", ""));
			postData.add(new BasicNameValuePair("__VIEWSTATE", strViewState));
			postData.add(new BasicNameValuePair("__EVENTVALIDATION", strEventValidation));
            postData.add(new BasicNameValuePair("ctl00$MainContent$SignIn1$txtEmail", username));
            postData.add(new BasicNameValuePair("ctl00$MainContent$SignIn1$txtPassword", password));
            postData.add(new BasicNameValuePair("ctl00$MainContent$SignIn1$btnLogin", "Logga in"));
			response = urlopen.open("https://www.payson.se/signin/", postData);
			//Helpers.slowDebug(TAG, response);
			
			if (response.contains("Felaktig E-postadress") || response.contains("Lösenord saknas") ||
			        response.contains("E-postadress saknas"))  {
				throw new LoginException(res.getText(R.string.invalid_username_password).toString());
			}
		}
		catch (ClientProtocolException e) {
			throw new BankException(e.getMessage());
		}
		catch (IOException e) {
			throw new BankException(e.getMessage());
		}
		return urlopen;
	}

	@Override
	public void update() throws BankException, LoginException {
		super.update();
		if (username == null || password == null || username.length() == 0 || password.length() == 0) {
			throw new LoginException(res.getText(R.string.invalid_username_password).toString());
		}
		urlopen = login();
		try {
			Matcher matcher;
			matcher = reBalance.matcher(response);
			if (matcher.find()) {
                /*
                 * Capture groups:
                 * GROUP                EXAMPLE DATA
                 * 1: Balance           0,00 kr
                 *  
                 */			    
				accounts.add(new Account("Konto" , Helpers.parseBalance(matcher.group(1)), "1"));
				balance = balance.add(Helpers.parseBalance(matcher.group(1)));
			}
			
			if (accounts.isEmpty()) {
				throw new BankException(res.getText(R.string.no_accounts_found).toString());
			}
	
	
			matcher = reTransactions.matcher(response);
			ArrayList<Transaction> transactions = new ArrayList<Transaction>();
			while (matcher.find()) {
                /*
                 * Capture groups:
                 * GROUP                EXAMPLE DATA
                 * 1: Date              2010-06-03
                 * 2: Specification     Best&#228;llning fr&#229;n SPELKONTROLL.SE
                 * 3: Amount            -228,00 kr
                 *   
                 */     
				transactions.add(new Transaction(matcher.group(1).trim(), Html.fromHtml(matcher.group(2)).toString().trim(), Helpers.parseBalance(matcher.group(3))));
			}
			accounts.get(0).setTransactions(transactions);
		}		
        finally {
            super.updateComplete();
        }
	}
}