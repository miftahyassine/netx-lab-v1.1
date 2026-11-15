package com.netxlab.app.di

import android.content.Context
import androidx.room.Room
import com.netxlab.app.data.NxDao
import com.netxlab.app.data.NxDb
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun db(@ApplicationContext ctx: Context): NxDb =
        Room.databaseBuilder(ctx, NxDb::class.java, "netxlab.db").fallbackToDestructiveMigration().build()

    @Provides @Singleton
    fun dao(db: NxDb): NxDao = db.dao()
}
