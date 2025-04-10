
package com.ponysdk.sample.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import com.ponysdk.core.server.application.Application;
import com.ponysdk.core.server.servlet.SessionManager;
import com.ponysdk.sample.client.activity.MarketData;

public class TradingServiceImpl {

    private final List<MarketData> marketDatas = new ArrayList<>();

    public TradingServiceImpl() {
        marketDatas.add(new MarketData("EurUSD", 0, 0));
        marketDatas.add(new MarketData("EurUSD1", 0, 0));
        marketDatas.add(new MarketData("EurUSD2", 0, 0));
        marketDatas.add(new MarketData("EurTKY", 0, 0));
        marketDatas.add(new MarketData("EurTKY1", 0, 0));
        marketDatas.add(new MarketData("EurTKY2", 0, 0));
        marketDatas.add(new MarketData("EurJP", 0, 0));
        marketDatas.add(new MarketData("EurJP1", 0, 0));
        marketDatas.add(new MarketData("EurJP2", 0, 0));
        marketDatas.add(new MarketData("EurCA", 0, 0));
        marketDatas.add(new MarketData("EurCA1", 0, 0));
        marketDatas.add(new MarketData("EurCA2", 0, 0));
        marketDatas.add(new MarketData("EurNY", 0, 0));
        marketDatas.add(new MarketData("EurNY1", 0, 0));
        marketDatas.add(new MarketData("EurNY2", 0, 0));
    }

    //
    // @Override
    // public List<MarketData> findCurrencies() throws Exception {
    // return marketDatas;
    // }

    public void start() {
        final Random rdm = new Random();
        final Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {

            @Override
            public void run() {
                final MarketData market = marketDatas.get(rdm.nextInt(marketDatas.size()));
                final MarketData price = new MarketData(market.getCurrency(), (int) (Math.random() * 99), (int) (Math.random() * 99));

                for (final Application application : SessionManager.get().getApplications()) {
                    application.pushToClients(price);
                }
            }
        }, 1000, 200);
    }

}
