package com.nearinfinity.blur.lucene.search;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryParser.CharStream;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.queryParser.QueryParserTokenManager;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.FilteredQuery;
import org.apache.lucene.search.MultiPhraseQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.Version;

import com.nearinfinity.blur.lucene.index.SuperDocument;
import com.nearinfinity.blur.lucene.search.cache.Acl;

public class SuperParser extends QueryParser {
	
	private static final String ON = "on";
	private static final String ENABLED = "enabled";
	private static final String TRUE = "true";
	private static final String _1 = "1";
	private static final String T = "t";
	private static final String FACETS = "facets";
	private static final String F = "f";
	private static final String _0 = "0";
	private static final String FALSE = "false";
	private static final String DISABLED = "disabled";
	private static final String OFF = "off";
	public static final String SUPER = "super";
	private Map<Query,String> fieldNames = new HashMap<Query, String>();
	private boolean superSearch = true;
	private boolean facetedSearch = false;
	private Acl acl;
	
	public static void main(String[] args) throws ParseException {
		SuperParser parser = new SuperParser(Version.LUCENE_CURRENT, new StandardAnalyzer(Version.LUCENE_CURRENT));
		Query query = parser.parse("address.street:sulgrave +(person.firstname:\"aaron patrick\" person.lastname:mccurry +(person.gender:(unknown male)))");
		System.out.println(query);
		Query query2 = parser.parse("disabled address.street:sulgrave +(person.firstname:\"aaron patrick\" person.lastname:mccurry +(person.gender:(unknown male)))");
		System.out.println(query2);
	}

	protected SuperParser(CharStream stream) {
		super(stream);
	}

	public SuperParser(QueryParserTokenManager tm) {
		super(tm);
	}

	public SuperParser(Version matchVersion, Analyzer a) {
		super(matchVersion, SUPER, a);
	}
	
	public SuperParser(Version matchVersion, Analyzer a, Acl acl) {
		super(matchVersion, SUPER, a);
		this.acl = acl;
	}

	@Override
	public Query parse(String query) throws ParseException {
		return reprocess(super.parse(query));
	}

	@Override
	protected Query newFuzzyQuery(Term term, float minimumSimilarity, int prefixLength) {
		return addField(super.newFuzzyQuery(term, minimumSimilarity, prefixLength),term.field());
	}

	@Override
	protected Query newMatchAllDocsQuery() {
		return addField(super.newMatchAllDocsQuery(),UUID.randomUUID().toString());
	}

	@Override
	protected MultiPhraseQuery newMultiPhraseQuery() {
		return new MultiPhraseQuery() {
			private static final long serialVersionUID = 2743009696906520410L;
			@Override
			public void add(Term[] terms, int position) {
				super.add(terms, position);
				for (Term term : terms) {
					addField(this, term.field());
				}
			}
		};
	}

	@Override
	protected PhraseQuery newPhraseQuery() {
		return new PhraseQuery() {
			private static final long serialVersionUID = 1927750709523859808L;
			@Override
			public void add(Term term, int position) {
				super.add(term, position);
				addField(this, term.field());
			}
		};
	}
	
	@Override
	protected Query newPrefixQuery(Term prefix) {
		return addField(super.newPrefixQuery(prefix),prefix.field());
	}

	@Override
	protected Query newRangeQuery(String field, String part1, String part2, boolean inclusive) {
		return addField(super.newRangeQuery(field, part1, part2, inclusive),field);
	}

	@Override
	protected Query newTermQuery(Term term) {
		if (isSuperSearchOffFlag(term)) {
			superSearch = false;
		}
		if (isFacetSearch(term)) {
			facetedSearch = true;
		}
		return addField(super.newTermQuery(term),term.field());
	}

	private boolean isFacetSearch(Term term) {
		if (term.field().toLowerCase().equals(FACETS) && isPositive(term.text())) {
			return true;
		}
		return false;
	}

	private boolean isPositive(String str) {
		str = str.toLowerCase();
		if (str.equals(ON) || str.equals(ENABLED) || str.equals(TRUE) || str.equals(_1) || str.equals(T)) {
			return true;
		}
		return false;
	}

	private boolean isSuperSearchOffFlag(Term term) {
		if (term.field().toLowerCase().equals(SUPER) && isNegative(term.text())) {
			return true;
		}
		return false;
	}

	private boolean isNegative(String str) {
		str = str.toLowerCase();
		if (str.equals(OFF) || str.equals(DISABLED) || str.equals(FALSE) || str.equals(_0) || str.equals(F)) {
			return true;
		}
		return false;
	}

	@Override
	protected Query newWildcardQuery(Term t) {
		return addField(super.newWildcardQuery(t),t.field());
	}

	private Query reprocess(Query query) {
		if (query == null || !isSuperSearch()) {
			return wrapAcl(query);
		}
		if (query instanceof BooleanQuery) {
			BooleanQuery booleanQuery = (BooleanQuery) query;
			if (isSameGroupName(booleanQuery)) {
				return new SuperQuery(booleanQuery);
			} else {
				List<BooleanClause> clauses = booleanQuery.clauses();
				for (BooleanClause clause : clauses) {
					clause.setQuery(reprocess(clause.getQuery()));
				}
				return booleanQuery;
			}
		} else {
			return new SuperQuery(wrapAcl(query));
		}
	}

	private Query wrapAcl(Query query) {
		if (acl == null) {
			return query;
		}
		return new FilteredQuery(query,acl);
	}

	private boolean isSameGroupName(BooleanQuery booleanQuery) {
		String groupName = findFirstGroupName(booleanQuery);
		if (groupName == null) {
			return false;
		}
		return isSameGroupName(booleanQuery,groupName);
	}
	
	private boolean isSameGroupName(Query query, String groupName) {
		if (query instanceof BooleanQuery) {
			BooleanQuery booleanQuery = (BooleanQuery) query;
			for (BooleanClause clause : booleanQuery.clauses()) {
				if (!isSameGroupName(clause.getQuery(), groupName)) {
					return false;
				}
			}
			return true;
		} else {
			String fieldName = fieldNames.get(query);
			String currentGroupName = getGroupName(fieldName);
			if (groupName.equals(currentGroupName)) {
				return true;
			}
			return false;
		}
	}

	private String getGroupName(String fieldName) {
		if (fieldName == null) {
			return null;
		}
		int index = fieldName.indexOf(SuperDocument.SEP);
		if (index < 0) {
			return null;
		}
		return fieldName.substring(0,index);
	}

	private String findFirstGroupName(Query query) {
		if (query instanceof BooleanQuery) {
			BooleanQuery booleanQuery = (BooleanQuery) query;
			for (BooleanClause clause : booleanQuery.clauses()) {
				return findFirstGroupName(clause.getQuery());
			}
			return null;
		} else {
			String fieldName = fieldNames.get(query);
			return getGroupName(fieldName);
		}
	}

	private Query addField(Query q, String field) {
		fieldNames.put(q, field);
		return q;
	}
	
	
	public boolean isSuperSearch() {
		if (facetedSearch) {
			superSearch = true;
		}
		return superSearch;
	}

	public boolean isFacetedSearch() {
		return facetedSearch;
	}
}