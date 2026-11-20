                val tradeViewModel: AddEditTradeViewModel = viewModel(
                    key = "add_edit_trade_${date}_$tradeId",
                    factory = AddEditTradeViewModel.provideFactory(
                        repository = repository,
                        date = date,
                        tradeId = tradeId,
                        alertManager = alertManager,
                        settingsStore = alertPreferences
                    )
                )