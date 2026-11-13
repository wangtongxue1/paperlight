package com.zhijian.reading.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File

/**
 * The library's database. It keeps calibre's own `metadata.db` schema
 * (user_version 28) so the folder stays a calibre library: copy it to a
 * desktop and calibre opens it, and copy a calibre library here and this
 * app reads it.
 *
 * calibre's triggers need SQL functions only calibre registers
 * (`title_sort`, `uuid4`), so none are created here; the writer keeps the
 * sort columns up to date itself.
 */
class LibraryDb(context: Context, root: File) :
    SQLiteOpenHelper(context, File(root, "metadata.db").absolutePath, null, VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        SCHEMA.trimIndent().split(";\n").forEach { stmt ->
            val s = stmt.trim()
            if (s.isNotEmpty()) db.execSQL(s)
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    companion object {
        /** calibre's schema version. */
        const val VERSION = 28

        private val SCHEMA = """
            CREATE TABLE authors (
                id INTEGER PRIMARY KEY, name TEXT NOT NULL COLLATE NOCASE,
                sort TEXT COLLATE NOCASE, link TEXT NOT NULL DEFAULT '', UNIQUE(name))
            ;
            CREATE TABLE books (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL DEFAULT 'Unknown' COLLATE NOCASE,
                sort TEXT COLLATE NOCASE,
                timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                pubdate TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                series_index REAL NOT NULL DEFAULT 1.0,
                author_sort TEXT COLLATE NOCASE,
                path TEXT NOT NULL DEFAULT '', uuid TEXT, has_cover BOOL DEFAULT 0,
                last_modified TIMESTAMP NOT NULL DEFAULT '2000-01-01 00:00:00+00:00')
            ;
            CREATE TABLE books_authors_link (id INTEGER PRIMARY KEY, book INTEGER NOT NULL,
                author INTEGER NOT NULL, UNIQUE(book, author))
            ;
            CREATE TABLE books_languages_link (id INTEGER PRIMARY KEY, book INTEGER NOT NULL,
                lang_code INTEGER NOT NULL, item_order INTEGER NOT NULL DEFAULT 0, UNIQUE(book, lang_code))
            ;
            CREATE TABLE books_publishers_link (id INTEGER PRIMARY KEY, book INTEGER NOT NULL,
                publisher INTEGER NOT NULL, UNIQUE(book))
            ;
            CREATE TABLE books_ratings_link (id INTEGER PRIMARY KEY, book INTEGER NOT NULL,
                rating INTEGER NOT NULL, UNIQUE(book, rating))
            ;
            CREATE TABLE books_series_link (id INTEGER PRIMARY KEY, book INTEGER NOT NULL,
                series INTEGER NOT NULL, UNIQUE(book))
            ;
            CREATE TABLE books_tags_link (id INTEGER PRIMARY KEY, book INTEGER NOT NULL,
                tag INTEGER NOT NULL, UNIQUE(book, tag))
            ;
            CREATE TABLE comments (id INTEGER PRIMARY KEY, book INTEGER NOT NULL,
                text TEXT NOT NULL COLLATE NOCASE, UNIQUE(book))
            ;
            CREATE TABLE data (id INTEGER PRIMARY KEY, book INTEGER NOT NULL,
                format TEXT NOT NULL COLLATE NOCASE, uncompressed_size INTEGER NOT NULL,
                name TEXT NOT NULL, UNIQUE(book, format))
            ;
            CREATE TABLE identifiers (id INTEGER PRIMARY KEY, book INTEGER NOT NULL,
                type TEXT NOT NULL DEFAULT 'isbn' COLLATE NOCASE, val TEXT NOT NULL COLLATE NOCASE,
                UNIQUE(book, type))
            ;
            CREATE TABLE languages (id INTEGER PRIMARY KEY, lang_code TEXT NOT NULL COLLATE NOCASE,
                link TEXT NOT NULL DEFAULT '', UNIQUE(lang_code))
            ;
            CREATE TABLE publishers (id INTEGER PRIMARY KEY, name TEXT NOT NULL COLLATE NOCASE,
                sort TEXT COLLATE NOCASE, UNIQUE(name))
            ;
            CREATE TABLE ratings (id INTEGER PRIMARY KEY, rating INTEGER NOT NULL,
                link TEXT NOT NULL DEFAULT '', UNIQUE(rating))
            ;
            CREATE TABLE series (id INTEGER PRIMARY KEY, name TEXT NOT NULL COLLATE NOCASE,
                sort TEXT COLLATE NOCASE, UNIQUE(name))
            ;
            CREATE TABLE tags (id INTEGER PRIMARY KEY, name TEXT NOT NULL COLLATE NOCASE,
                link TEXT NOT NULL DEFAULT '', UNIQUE(name))
            ;
            CREATE TABLE metadata_dirtied (id INTEGER PRIMARY KEY, book INTEGER NOT NULL, UNIQUE(book))
            ;
            CREATE INDEX books_authors_link_aidx ON books_authors_link (author)
            ;
            CREATE INDEX books_authors_link_bidx ON books_authors_link (book)
            ;
            CREATE INDEX books_tags_link_aidx ON books_tags_link (tag)
            ;
            CREATE INDEX books_tags_link_bidx ON books_tags_link (book)
            ;
            CREATE INDEX books_series_link_aidx ON books_series_link (series)
            ;
            CREATE INDEX books_series_link_bidx ON books_series_link (book)
            ;
            CREATE INDEX authors_idx ON authors (name)
            ;
            CREATE INDEX tags_idx ON tags (name)
            ;
            CREATE INDEX data_book ON data (book)
        """
    }
}
