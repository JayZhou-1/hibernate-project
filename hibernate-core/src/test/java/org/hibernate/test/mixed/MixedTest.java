/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.test.mixed;

import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.dialect.SybaseASE15Dialect;

import org.hibernate.testing.DialectChecks;
import org.hibernate.testing.RequiresDialectFeature;
import org.hibernate.testing.SkipForDialect;
import org.hibernate.testing.SkipLog;
import org.hibernate.testing.junit4.BaseCoreFunctionalTestCase;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author Gavin King
 */
@SkipForDialect( SybaseASE15Dialect.class )
@RequiresDialectFeature(value = { DialectChecks.NotH2Version2.class}, comment = "See https://github.com/h2database/h2database/issues/3385")
public class MixedTest extends BaseCoreFunctionalTestCase {
	@Override
	public String[] getMappings() {
		return new String[]{"mixed/Item.hbm.xml"};
	}

	@Test
	public void testMixedInheritance() {
		Session s = openSession( new DocumentInterceptor() );
		Transaction t = s.beginTransaction();
		Folder f = new Folder();
		f.setName( "/" );
		s.save( f );

		Document d = new Document();
		d.setName( "Hibernate in Action" );
		d.setContent( s.getLobHelper().createBlob( "blah blah blah".getBytes() ) );
		d.setParent( f );
		Long did = (Long) s.save( d );

		SecureDocument d2 = new SecureDocument();
		d2.setName( "Secret" );
		d2.setContent( s.getLobHelper().createBlob( "wxyz wxyz".getBytes() ) );
		// SybaseASE15Dialect only allows 7-bits in a byte to be inserted into a tinyint 
		// column (0 <= val < 128)
		d2.setPermissionBits( (byte) 127 );
		d2.setOwner( "gavin" );
		d2.setParent( f );
		Long d2id = (Long) s.save( d2 );

		t.commit();
		s.close();

		if ( ! getDialect().supportsExpectedLobUsagePattern() ) {
			SkipLog.reportSkip( "database/driver does not support expected LOB usage pattern", "LOB support" );
			return;
		}

		s = openSession( new DocumentInterceptor() );
		t = s.beginTransaction();
		Item id = (Item) s.load( Item.class, did );
		assertEquals( did, id.getId() );
		assertEquals( "Hibernate in Action", id.getName() );
		assertEquals( "/", id.getParent().getName() );

		Item id2 = (Item) s.load( Item.class, d2id );
		assertEquals( d2id, id2.getId() );
		assertEquals( "Secret", id2.getName() );
		assertEquals( "/", id2.getParent().getName() );

		id.setName( "HiA" );

		d2 = (SecureDocument) s.load( SecureDocument.class, d2id );
		d2.setOwner( "max" );

		s.flush();

		s.clear();

		d = (Document) s.load( Document.class, did );
		assertEquals( did, d.getId() );
		assertEquals( "HiA", d.getName() );
		assertNotNull( d.getContent() );
		assertEquals( "/", d.getParent().getName() );
		assertNotNull( d.getCreated() );
		assertNotNull( d.getModified() );

		d2 = (SecureDocument) s.load( SecureDocument.class, d2id );
		assertEquals( d2id, d2.getId() );
		assertEquals( "Secret", d2.getName() );
		assertNotNull( d2.getContent() );
		assertEquals( "max", d2.getOwner() );
		assertEquals( "/", d2.getParent().getName() );
		// SybaseASE15Dialect only allows 7-bits in a byte to be inserted into a tinyint 
		// column (0 <= val < 128)
		assertEquals( (byte) 127, d2.getPermissionBits() );
		assertNotNull( d2.getCreated() );
		assertNotNull( d2.getModified() );

		s.delete( d.getParent() );
		s.delete( d );
		s.delete( d2 );

		t.commit();
		s.close();
	}
}

